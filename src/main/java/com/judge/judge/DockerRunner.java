package com.judge.judge;

import com.judge.config.JudgeConfig;
import com.judge.judge.model.CheckerResult;
import com.judge.judge.model.CompileResult;
import com.judge.judge.model.InteractiveResult;
import com.judge.judge.model.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class DockerRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRunner.class);

    private final JudgeConfig judgeConfig;

    public DockerRunner(JudgeConfig judgeConfig) {
        this.judgeConfig = judgeConfig;
    }

    public CompileResult compile(String language, String sourceCode, String jobId) throws IOException {
        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        Path workDir = Path.of(judgeConfig.getWorkBase(), jobId);
        Files.createDirectories(workDir);
        makeWritableByAll(workDir);

        Files.writeString(workDir.resolve(lang.getSourceFile()), sourceCode);

        if (lang.getCompileCmd() == null || lang.getCompileCmd().isBlank()) {
            return CompileResult.builder().success(true).workDir(workDir.toString()).build();
        }

        List<String> cmd = buildDockerCmd(lang.getImage(), workDir.toString(), lang.getCompileCmd(), true);
        ProcessResult result = runProcess(cmd, judgeConfig.getCompileTimeoutMs());

        if (result.exitCode() != 0) {
            String output = result.stderr().isBlank() ? result.stdout() : result.stderr();
            if (isDockerDaemonError(output)) {
                log.error("Docker daemon unavailable during compile, jobId={}: {}", jobId, output.trim());
                return CompileResult.builder()
                        .success(false)
                        .systemError(true)
                        .workDir(workDir.toString())
                        .errorOutput(output.trim())
                        .build();
            }
            return CompileResult.builder()
                    .success(false)
                    .workDir(workDir.toString())
                    .errorOutput(output)
                    .build();
        }

        return CompileResult.builder().success(true).workDir(workDir.toString()).build();
    }

    public record BatchTestCase(Long id, String inputPath) {}

    public record BatchRunResult(
            boolean compileSuccess,
            String compileError,
            boolean systemError,
            Map<Long, RunResult> results
    ) {}

    public RunResult run(String workDir, String language, String inputPath,
                         int timeLimitMs, int memoryKb) {
        Map<Long, RunResult> map = runBatch(workDir, language,
                List.of(new BatchTestCase(1L, inputPath)),
                timeLimitMs, memoryKb, false);
        return map.getOrDefault(1L, RunResult.dockerUnavailable("no result returned"));
    }

    public Map<Long, RunResult> runBatch(String workDir, String language,
                                         List<BatchTestCase> testCases,
                                         int timeLimitMs, int memoryKb,
                                         boolean stopOnFail) {
        return runBatchWithCompile(workDir, language, null, testCases, timeLimitMs, memoryKb, stopOnFail).results();
    }

    public BatchRunResult runBatchWithCompile(String workDir, String language,
                                              String compileCmd,
                                              List<BatchTestCase> testCases,
                                              int timeLimitMs, int memoryKb,
                                              boolean stopOnFail) {
        if (testCases == null || testCases.isEmpty()) {
            return new BatchRunResult(true, null, false, Collections.emptyMap());
        }

        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        double multiplier = lang.getTimeMultiplier() > 0 ? lang.getTimeMultiplier() : 1.0;
        long effectiveLimitMs = Math.round(timeLimitMs * multiplier);

        int memMb = (int) Math.max((memoryKb + lang.getMemoryBonusKb()) / 1024, 64);
        int containerMemMb = (compileCmd != null && !compileCmd.isBlank()) ? Math.max(memMb, 512) : memMb;
        double wallSecs = effectiveLimitMs / 1000.0 + 1.0;
        String runCmd = lang.getRunCmd().replace("{mem}", String.valueOf(memMb));

        Path metricsDir = Path.of(workDir, "metrics");
        try {
            Files.createDirectories(metricsDir);
            makeWritableByAll(metricsDir);
        } catch (IOException e) {
            log.error("Cannot create metrics dir: {}", metricsDir, e);
            Map<Long, RunResult> errMap = new HashMap<>();
            for (BatchTestCase tc : testCases) {
                errMap.put(tc.id(), RunResult.dockerUnavailable("cannot create metrics dir: " + e.getMessage()));
            }
            return new BatchRunResult(true, null, false, errMap);
        }

        // Generate batch_run.sh
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/sh\nset +e\nmkdir -p /metrics\n");
        if (compileCmd != null && !compileCmd.isBlank()) {
            script.append(String.format(
                    "%s 2> /metrics/compile.err\n" +
                    "COMPILE_EC=$?\n" +
                    "if [ \"$COMPILE_EC\" -ne 0 ]; then\n" +
                    "  echo $COMPILE_EC > /metrics/compile.exit\n" +
                    "  exit 0\n" +
                    "fi\n" +
                    "chmod 555 /code/solution 2>/dev/null || true\n",
                    compileCmd
            ));
        }

        for (BatchTestCase tc : testCases) {
            String containerInput = resolveContainerPath(tc.inputPath(), workDir);
            script.append(String.format(
                    "/usr/bin/time -q -f '%%e %%U %%S %%M %%x' -o /metrics/%d.time timeout %s /bin/sh -c '%s < \"%s\" > /metrics/%d.out 2> /metrics/%d.err'\n" +
                    "EC=$?\n" +
                    "echo $EC > /metrics/%d.exit\n",
                    tc.id(), fmt(wallSecs), runCmd, containerInput, tc.id(), tc.id(), tc.id()
            ));
            if (stopOnFail) {
                script.append("if [ \"$EC\" -ne 0 ]; then exit 0; fi\n");
            }
        }

        Path scriptPath = Path.of(workDir, "batch_run.sh");
        try {
            Files.writeString(scriptPath, script.toString());
            makeWritableByAll(scriptPath);
        } catch (IOException e) {
            log.error("Failed to write batch script: {}", scriptPath, e);
            Map<Long, RunResult> errMap = new HashMap<>();
            for (BatchTestCase tc : testCases) {
                errMap.put(tc.id(), RunResult.dockerUnavailable("cannot write batch script: " + e.getMessage()));
            }
            return new BatchRunResult(true, null, false, errMap);
        }

        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", containerMemMb + "m",
                "--memory-swap", containerMemMb + "m",
                "--cpus", fmt(judgeConfig.getSandboxCpus()),
                "--pids-limit", "64",
                "-v", workDir + ":/code" + (compileCmd != null && !compileCmd.isBlank() ? ":rw" : ":ro"),
                "-v", metricsDir.toString() + ":/metrics",
                "-v", judgeConfig.getTestcaseBasePath() + ":" + judgeConfig.getTestcaseBasePath() + ":ro",
                "-w", "/code",
                lang.getImage(),
                "/bin/sh", "/code/batch_run.sh"
        ));

        long cap = judgeConfig.getOutputLimitBytes();
        long compileBufferMs = (compileCmd != null && !compileCmd.isBlank()) ? judgeConfig.getCompileTimeoutMs() : 0;
        long totalTimeoutMs = Math.max(testCases.size() * (effectiveLimitMs + 1500L) + compileBufferMs + 5000L, 30000L);

        Map<Long, RunResult> results = new HashMap<>();
        try {
            ProcessResult result = runProcess(cmd, totalTimeoutMs);

            if (isDockerDaemonError(result.stderr())) {
                log.error("Docker daemon unavailable during batch run, workDir={}: {}", workDir, result.stderr().trim());
                for (BatchTestCase tc : testCases) {
                    results.put(tc.id(), RunResult.dockerUnavailable(result.stderr().trim()));
                }
                return new BatchRunResult(true, null, true, results);
            }

            Path compileExitFile = metricsDir.resolve("compile.exit");
            if (Files.exists(compileExitFile)) {
                String compileErr = readCappedFile(metricsDir.resolve("compile.err"), 65536);
                if (compileErr == null || compileErr.isBlank()) {
                    compileErr = "Compilation failed with exit code " + readMetricsFile(metricsDir, "compile.exit");
                }
                return new BatchRunResult(false, compileErr.trim(), isDockerDaemonError(compileErr), Collections.emptyMap());
            }

            for (BatchTestCase tc : testCases) {
                Path timeFile = metricsDir.resolve(tc.id() + ".time");
                Path exitFile = metricsDir.resolve(tc.id() + ".exit");
                if (!Files.exists(timeFile) && !Files.exists(exitFile)) {
                    // Not executed (e.g. stopped early due to stopOnFail)
                    continue;
                }

                RunMetrics m = parseMetrics(readMetricsFile(metricsDir, tc.id() + ".time"));
                int rawExit = parseIntSafe(readMetricsFile(metricsDir, tc.id() + ".exit"), m != null ? m.exitStatus() : -1);

                if (result.timedOut() || rawExit == 124) {
                    results.put(tc.id(), RunResult.tle(m != null ? m.cpuTimeMs() : effectiveLimitMs));
                    continue;
                }

                long memKb = m != null ? m.maxRssKb() : 0;
                long cpuMs = m != null ? m.cpuTimeMs() : 0;
                String outStr = readCappedFile(metricsDir.resolve(tc.id() + ".out"), cap);
                String errStr = readCappedFile(metricsDir.resolve(tc.id() + ".err"), cap);

                if (memoryKb > 0 && memKb >= memoryKb) {
                    results.put(tc.id(), RunResult.mle(memKb));
                    continue;
                }
                if (rawExit == 137) {
                    results.put(tc.id(), RunResult.mle(memKb));
                    continue;
                }
                if (m != null && cpuMs > effectiveLimitMs) {
                    results.put(tc.id(), RunResult.builder()
                            .timedOut(true).exitCode(124)
                            .timeMs(cpuMs).wallTimeMs(m.wallTimeMs()).memoryKb(memKb)
                            .stdout("").stderr("Time Limit Exceeded").build());
                    continue;
                }

                int childExit = m != null ? m.exitStatus() : rawExit;
                results.put(tc.id(), RunResult.builder()
                        .stdout(outStr)
                        .stderr(errStr.isEmpty() ? (childExit != 0 ? "Process exited with code " + childExit : "") : errStr)
                        .exitCode(childExit)
                        .timeMs(cpuMs)
                        .wallTimeMs(m != null ? m.wallTimeMs() : 0)
                        .memoryKb(memKb)
                        .build());
            }

            return new BatchRunResult(true, null, false, results);

        } catch (IOException e) {
            log.error("Docker batch run failed, workDir={}", workDir, e);
            for (BatchTestCase tc : testCases) {
                results.put(tc.id(), RunResult.dockerUnavailable(e.getMessage()));
            }
            return new BatchRunResult(true, null, false, results);
        }
    }

    static String resolveContainerPath(String hostPath, String workDir) {
        if (hostPath == null) return "";
        if (hostPath.startsWith(workDir)) {
            return "/code" + hostPath.substring(workDir.length());
        }
        return hostPath;
    }

    private static String fmt(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private String readMetricsFile(Path metricsDir, String name) {
        try {
            Path f = metricsDir.resolve(name);
            return Files.exists(f) ? Files.readString(f).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads at most `cap` bytes of a solution's captured output file. */
    private String readCappedFile(Path f, long cap) {
        if (!Files.exists(f)) return "";
        try (InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(cap, 8 * 1024 * 1024)];
            int total = 0, n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) != -1) {
                total += n;
            }
            return new String(buf, 0, total, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Parses a GNU time line "wall user sys maxRSS_kb exit"; null if malformed. */
    static RunMetrics parseMetrics(String line) {
        if (line == null || line.isBlank()) return null;
        // GNU time may prepend a "Command terminated by signal N" line on kills.
        String last = line.lines().reduce("", (a, b) -> b).trim();
        String[] p = last.split("\\s+");
        if (p.length < 5) return null;
        try {
            long wallMs = Math.round(Double.parseDouble(p[0]) * 1000);
            long cpuMs = Math.round((Double.parseDouble(p[1]) + Double.parseDouble(p[2])) * 1000);
            long rssKb = Long.parseLong(p[3]);
            int exit = Integer.parseInt(p[4]);
            return new RunMetrics(wallMs, cpuMs, rssKb, exit);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record RunMetrics(long wallTimeMs, long cpuTimeMs, long maxRssKb, int exitStatus) {}

    // Checker source/binary names are independent of the solution's LanguageConfig.
    private static String checkerSourceName(String language) {
        return switch (language) {
            case "cpp" -> "checker.cpp";
            case "java" -> "Checker.java";
            case "python" -> "checker.py";
            default -> throw new IllegalArgumentException("Unsupported checker language: " + language);
        };
    }

    private String checkerImage(String language) {
        JudgeConfig.LanguageConfig lang = judgeConfig.getLanguages().get(language);
        String image = lang != null ? lang.getImage() : null;
        return image != null ? image : "judge-cpp:1";
    }

    /**
     * Compiles a custom checker (or interactor). Returns the checker directory,
     * which is stored as checker_bin_path. Throws on compile failure.
     */
    public String compileChecker(String language, String sourceCode, Long problemId) throws IOException {
        Path checkerDir = Path.of(judgeConfig.getTestcaseBasePath(), String.valueOf(problemId), "checker");
        Files.createDirectories(checkerDir);
        makeWritableByAll(checkerDir);

        Files.writeString(checkerDir.resolve(checkerSourceName(language)), sourceCode);

        String compileCmd = switch (language) {
            case "cpp" -> "g++ -O2 -std=c++17 -o checker checker.cpp";
            case "java" -> "javac Checker.java";
            case "python" -> null;   // interpreted
            default -> throw new IllegalArgumentException("Unsupported checker language: " + language);
        };
        if (compileCmd != null) {
            List<String> cmd = buildDockerCmd(checkerImage(language), checkerDir.toString(), compileCmd, true);
            ProcessResult result = runProcess(cmd, judgeConfig.getCompileTimeoutMs());
            if (result.exitCode() != 0) {
                String err = result.stderr().isBlank() ? result.stdout() : result.stderr();
                throw new IOException("Checker compilation failed: " + err);
            }
        }
        return checkerDir.toString();
    }

    /**
     * Runs a custom checker: {@code checker <input> <expected> <actual>}.
     * Exit 0 = AC, 1 = WA, 7 = partial (first stdout line = 0..1 ratio), else SE.
     */
    public CheckerResult runChecker(String checkerDirPath, String checkerLanguage,
                                    String inputPath, String expectedPath,
                                    String actualOutput, String workDir) throws IOException {
        Path checkerDir = Path.of(checkerDirPath);
        // Legacy rows stored the binary path, not the dir — tolerate both.
        if (!Files.isDirectory(checkerDir)) checkerDir = checkerDir.getParent();
        Path actualFile = Path.of(workDir, "actual.txt");
        Files.writeString(actualFile, actualOutput);

        String lang = checkerLanguage != null ? checkerLanguage : "cpp";
        String checkerCmd = switch (lang) {
            case "cpp" -> "/checker/checker /input.txt /expected.txt /actual.txt";
            case "java" -> "java -cp /checker Checker /input.txt /expected.txt /actual.txt";
            case "python" -> "python3 /checker/checker.py /input.txt /expected.txt /actual.txt";
            default -> "/checker/checker /input.txt /expected.txt /actual.txt";
        };

        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", "256m",
                "--memory-swap", "256m",
                "--pids-limit", "64",
                "--cpus", fmt(judgeConfig.getSandboxCpus()),
                "-v", checkerDir.toString() + ":/checker:ro",
                "-v", inputPath + ":/input.txt:ro",
                "-v", expectedPath + ":/expected.txt:ro",
                "-v", actualFile.toString() + ":/actual.txt:ro",
                checkerImage(lang),
                "/bin/sh", "-c", checkerCmd
        ));

        ProcessResult result = runProcess(cmd, 10_000);
        return switch (result.exitCode()) {
            case 0 -> CheckerResult.ac();
            case 1 -> CheckerResult.wa();
            case 7 -> CheckerResult.pc(parseRatio(result.stdout()));
            default -> CheckerResult.se();
        };
    }

    private static double parseRatio(String stdout) {
        if (stdout == null || stdout.isBlank()) return 0.0;
        try {
            return Double.parseDouble(stdout.lines().findFirst().orElse("0").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Interactive judging: the solution and a compiled interactor talk over two
     * FIFOs inside one hardened container. The interactor is passed the input and
     * expected-answer files and its exit code is the verdict (0 AC / 1 WA /
     * 7 partial with the ratio in /metrics/score.txt). Solution TLE/MLE/RE take
     * precedence over the interactor's verdict.
     */
    public InteractiveResult runInteractive(String workDir, String language,
                                            String checkerDirPath, String checkerLanguage,
                                            String inputPath, String answerPath,
                                            int timeLimitMs, int memoryKb) {
        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        double multiplier = lang.getTimeMultiplier() > 0 ? lang.getTimeMultiplier() : 1.0;
        long effectiveLimitMs = Math.round(timeLimitMs * multiplier);
        int memMb = (int) Math.max((memoryKb + lang.getMemoryBonusKb()) / 1024, 64);
        int cpuSecs = (int) Math.max(effectiveLimitMs / 1000 + 1, 2);
        double wallSecs = effectiveLimitMs / 1000.0 + 1.0;
        String runCmd = lang.getRunCmd().replace("{mem}", String.valueOf(memMb));

        Path checkerDir = Path.of(checkerDirPath);
        if (!Files.isDirectory(checkerDir)) checkerDir = checkerDir.getParent();
        Path metricsDir = Path.of(workDir, "metrics");
        try {
            Files.createDirectories(metricsDir);
            makeWritableByAll(metricsDir);
        } catch (IOException e) {
            return new InteractiveResult("SE", 0, 0, 0, true);
        }

        String clang = checkerLanguage != null ? checkerLanguage : "cpp";
        String interactorCmd = switch (clang) {
            case "java" -> "java -cp /checker Checker /input.txt /answer.txt";
            case "python" -> "python3 /checker/checker.py /input.txt /answer.txt";
            default -> "/checker/checker /input.txt /answer.txt";
        };
        // FIFO open order matters: both the interactor and the solution must first
        // rendezvous on i2s (interactor opens it for WRITE, solution for READ) or the
        // two O_RDONLY opens deadlock. Hence interactor lists `> i2s` before `< s2i`.
        String inner = String.join("\n",
                "mkfifo /tmp/s2i /tmp/i2s",
                interactorCmd + " > /tmp/i2s < /tmp/s2i & IPID=$!",
                "timeout " + fmt(wallSecs) + " /usr/bin/time -q -f '%e %U %S %M %x' -o /metrics/run.txt"
                        + " /bin/sh -c '" + runCmd + "' < /tmp/i2s > /tmp/s2i 2> /metrics/err.txt",
                "wait $IPID; echo $? > /metrics/interactor_exit.txt");

        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", memMb + "m",
                "--memory-swap", memMb + "m",
                "--cpus", fmt(judgeConfig.getSandboxCpus()),
                "--pids-limit", "64",
                "--ulimit", "cpu=" + cpuSecs + ":" + cpuSecs,
                "-v", workDir + ":/code:ro",
                "-v", checkerDir.toString() + ":/checker:ro",
                "-v", metricsDir.toString() + ":/metrics",
                "-v", inputPath + ":/input.txt:ro",
                "-v", answerPath + ":/answer.txt:ro",
                "-w", "/code",
                lang.getImage(),
                "/bin/sh", "-c", inner
        ));

        try {
            ProcessResult result = runProcess(cmd, effectiveLimitMs + 5000L);
            if (isDockerDaemonError(result.stderr())) {
                return new InteractiveResult("SE", 0, 0, 0, true);
            }
            RunMetrics m = parseMetrics(readMetricsFile(metricsDir, "run.txt"));
            long memKb = m != null ? m.maxRssKb() : 0;
            long cpuMs = m != null ? m.cpuTimeMs() : 0;

            // Real resource limits take precedence over anything the interactor says.
            if (result.timedOut() || result.exitCode() == 124
                    || (m != null && cpuMs > effectiveLimitMs)) {
                return new InteractiveResult("TLE", 0, cpuMs, memKb, false);
            }
            if (memoryKb > 0 && memKb >= memoryKb) {
                return new InteractiveResult("MLE", 0, cpuMs, memKb, false);
            }

            // The interactor's verdict is authoritative: once it decides WA/PC it
            // closes the pipe, so the solution hitting EOF and exiting non-zero is
            // expected, not a genuine RE.
            int interactorExit = parseIntSafe(readMetricsFile(metricsDir, "interactor_exit.txt"), -1);
            int solExit = m != null ? m.exitStatus() : result.exitCode();
            return switch (interactorExit) {
                case 0 -> solExit != 0
                        ? new InteractiveResult("RE", 0, cpuMs, memKb, false)  // interactor happy but solution crashed
                        : new InteractiveResult("AC", 1.0, cpuMs, memKb, false);
                case 1 -> new InteractiveResult("WA", 0.0, cpuMs, memKb, false);
                case 7 -> {
                    double ratio = parseRatio(readMetricsFile(metricsDir, "score.txt"));
                    yield new InteractiveResult(ratio >= 1.0 ? "AC" : "PC", Math.max(0, Math.min(1, ratio)),
                            cpuMs, memKb, false);
                }
                default -> new InteractiveResult("SE", 0, cpuMs, memKb, true);
            };
        } catch (IOException e) {
            log.error("Interactive run failed, workDir={}", workDir, e);
            return new InteractiveResult("SE", 0, 0, 0, true);
        }
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    public void cleanup(String jobId) {
        Path workDir = Path.of(judgeConfig.getWorkBase(), jobId);
        try {
            deleteRecursively(workDir);
        } catch (IOException e) {
            log.warn("Failed to cleanup workDir={}", workDir, e);
        }
    }

    private JudgeConfig.LanguageConfig getLanguageConfig(String language) {
        JudgeConfig.LanguageConfig lang = judgeConfig.getLanguages().get(language);
        if (lang == null) throw new IllegalArgumentException("Unsupported language: " + language);
        return lang;
    }

    /**
     * Flags shared by every sandbox container (run, compile, checker):
     * no network, no capabilities, no privilege escalation, non-root user,
     * read-only root fs with a small writable /tmp, bounded fds and file sizes.
     */
    private List<String> baseSandboxFlags() {
        return List.of(
                "--network", "none",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--user", "1000:1000",
                "--ulimit", "nofile=256:256",
                "--ulimit", "fsize=67108864",
                "--read-only",
                "--tmpfs", "/tmp:size=64m,noexec,nosuid,nodev"
        );
    }

    /** Sandbox runs as uid 1000; dirs created by the (root) API process must be opened up. */
    static void makeWritableByAll(Path dir) {
        try {
            Files.setPosixFilePermissions(dir,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
        } catch (IOException e) {
            log.warn("Failed to chmod {}", dir, e);
        }
    }

    private List<String> buildDockerCmd(String image, String workDir, String shellCmd, boolean rw) {
        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", "1g",
                "--memory-swap", "1g",
                "--pids-limit", "128",
                "-v", workDir + ":/code" + (rw ? "" : ":ro"),
                "-w", "/code",
                image,
                "/bin/sh", "-c", shellCmd
        ));
        return cmd;
    }

    private ProcessResult runProcess(List<String> cmd, long timeoutMs) throws IOException {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();

        long cap = judgeConfig.getOutputLimitBytes();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        // Bounded readers: keep at most `cap` bytes, drain the rest so the child
        // never blocks on a full pipe but can't OOM this JVM either.
        Thread outReader = new Thread(() -> readBounded(process.getInputStream(), stdout, cap));
        Thread errReader = new Thread(() -> readBounded(process.getErrorStream(), stderr, cap));
        outReader.start();
        errReader.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ProcessResult("", "", -1, true);
        }

        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult("", "", 124, true);
        }

        try { outReader.join(5000); } catch (InterruptedException ignored) {}
        try { errReader.join(5000); } catch (InterruptedException ignored) {}

        return new ProcessResult(stdout.toString(), stderr.toString(), process.exitValue(), false);
    }

    private static void readBounded(InputStream in, StringBuilder sink, long cap) {
        byte[] buf = new byte[8192];
        long total = 0;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (total < cap) {
                    int take = (int) Math.min(n, cap - total);
                    sink.append(new String(buf, 0, take, java.nio.charset.StandardCharsets.UTF_8));
                }
                total += n;
            }
        } catch (IOException ignored) {}
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) {}
                    });
        }
    }

    /**
     * Distinguishes infrastructure failure (daemon down, CLI missing) from a
     * user program's own stderr. Anchors to the docker CLI's own message lines
     * so a program printing "executable file not found" can't be misread as SE.
     */
    static boolean isDockerDaemonError(String text) {
        if (text == null || text.isBlank()) return false;
        return text.lines().anyMatch(line -> {
            String l = line.trim();
            return l.startsWith("Cannot connect to the Docker daemon")
                    || l.startsWith("docker: Cannot connect")
                    || l.contains("Is the docker daemon running")
                    || l.startsWith("docker: not found")
                    || l.startsWith("docker: command not found")
                    || l.startsWith("error during connect");
        });
    }

    private record ProcessResult(String stdout, String stderr, int exitCode, boolean timedOut) {}
}
