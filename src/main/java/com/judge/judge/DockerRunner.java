package com.judge.judge;

import com.judge.config.JudgeConfig;
import com.judge.judge.model.CompileResult;
import com.judge.judge.model.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    public RunResult run(String workDir, String language, String inputPath,
                         int timeLimitMs, int memoryKb) {
        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        double multiplier = lang.getTimeMultiplier() > 0 ? lang.getTimeMultiplier() : 1.0;
        long effectiveLimitMs = Math.round(timeLimitMs * multiplier);

        // Container memory cap = verdict limit + language bonus (JVM needs headroom
        // just to start). The MLE verdict is still judged against the raw memoryKb.
        int memMb = (int) Math.max((memoryKb + lang.getMemoryBonusKb()) / 1024, 64);
        int cpuSecs = (int) Math.max(effectiveLimitMs / 1000 + 1, 2);
        // Wall-clock guard is generous — it only catches hangs/sleeps, not CPU-bound TLE.
        double wallSecs = effectiveLimitMs / 1000.0 + 1.0;
        String runCmd = lang.getRunCmd().replace("{mem}", String.valueOf(memMb));

        Path metricsDir = Path.of(workDir, "metrics");
        try {
            Files.createDirectories(metricsDir);
            makeWritableByAll(metricsDir);
        } catch (IOException e) {
            return RunResult.dockerUnavailable("cannot create metrics dir: " + e.getMessage());
        }

        long cap = judgeConfig.getOutputLimitBytes();
        // Solution stdout goes to a file (bounded by the fsize ulimit) so the shell
        // exit status reflects the SOLUTION's exit code — piping through `head` would
        // mask it, and dash has no `pipefail`. GNU time writes
        // "wall user sys maxRSS_kb exit" to /metrics/run.txt.
        String inner = "timeout " + fmt(wallSecs)
                + " /usr/bin/time -q -f '%e %U %S %M %x' -o /metrics/run.txt"
                + " /bin/sh -c '" + runCmd + " < /input.txt > /metrics/out.txt 2> /metrics/err.txt'";

        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", memMb + "m",
                "--memory-swap", memMb + "m",
                "--cpus", fmt(judgeConfig.getSandboxCpus()),
                "--pids-limit", "64",
                "--ulimit", "cpu=" + cpuSecs + ":" + cpuSecs,
                "-v", workDir + ":/code:ro",
                "-v", metricsDir.toString() + ":/metrics",
                "-v", inputPath + ":/input.txt:ro",
                "-w", "/code",
                lang.getImage(),
                "/bin/sh", "-c", inner
        ));

        try {
            ProcessResult result = runProcess(cmd, effectiveLimitMs + 5000L);

            if (isDockerDaemonError(result.stderr())) {
                log.error("Docker daemon unavailable during run, workDir={}: {}", workDir, result.stderr().trim());
                return RunResult.dockerUnavailable(result.stderr().trim());
            }

            RunMetrics m = parseMetrics(readMetricsFile(metricsDir, "run.txt"));

            // Outer docker/timeout kill (hang or sleep-bound): wall-clock guard.
            if (result.timedOut() || result.exitCode() == 124) {
                return RunResult.tle(m != null ? m.cpuTimeMs() : effectiveLimitMs);
            }

            long memKb = m != null ? m.maxRssKb() : 0;
            long cpuMs = m != null ? m.cpuTimeMs() : 0;
            String outStr = readCappedFile(metricsDir.resolve("out.txt"), cap);
            String errStr = readCappedFile(metricsDir.resolve("err.txt"), cap);

            // MLE before RE: an OOM abort (exit 137, or C++ bad_alloc -> nonzero exit)
            // is a memory problem, not a runtime error.
            if (memoryKb > 0 && memKb >= memoryKb) {
                return RunResult.mle(memKb);
            }
            if (result.exitCode() == 137) {
                return RunResult.mle(memKb);
            }
            // CPU-time TLE: the honest limit check, independent of host load / spin-up.
            if (m != null && cpuMs > effectiveLimitMs) {
                return RunResult.builder()
                        .timedOut(true).exitCode(124)
                        .timeMs(cpuMs).wallTimeMs(m.wallTimeMs()).memoryKb(memKb)
                        .stdout("").stderr("Time Limit Exceeded").build();
            }

            // Exit status from GNU time (%x) is the child's real exit code; the docker
            // process exit reflects the `time` wrapper which is usually 0.
            int childExit = m != null ? m.exitStatus() : result.exitCode();

            return RunResult.builder()
                    .stdout(outStr)
                    .stderr(errStr.isEmpty() ? result.stderr() : errStr)
                    .exitCode(childExit)
                    .timeMs(cpuMs)
                    .wallTimeMs(m != null ? m.wallTimeMs() : 0)
                    .memoryKb(memKb)
                    .build();

        } catch (IOException e) {
            log.error("Docker run failed (binary unavailable?), workDir={}", workDir, e);
            return RunResult.dockerUnavailable(e.getMessage());
        }
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

    /**
     * Compiles a custom checker. Returns the path to the checker binary, or throws on failure.
     */
    public String compileChecker(String language, String sourceCode, Long problemId) throws IOException {
        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        Path checkerDir = Path.of(judgeConfig.getTestcaseBasePath(), String.valueOf(problemId), "checker");
        Files.createDirectories(checkerDir);
        makeWritableByAll(checkerDir);

        Files.writeString(checkerDir.resolve(lang.getSourceFile()), sourceCode);

        if (lang.getCompileCmd() != null && !lang.getCompileCmd().isBlank()) {
            List<String> cmd = buildDockerCmd(lang.getImage(), checkerDir.toString(), lang.getCompileCmd(), true);
            ProcessResult result = runProcess(cmd, 60_000);
            if (result.exitCode() != 0) {
                String err = result.stderr().isBlank() ? result.stdout() : result.stderr();
                throw new IOException("Checker compilation failed: " + err);
            }
        }

        // Return the directory; checker binary name depends on language
        String binaryName = switch (language) {
            case "cpp" -> "solution";
            case "java" -> "checker_dir";
            default -> "checker_dir";
        };
        return checkerDir.resolve(binaryName).toString();
    }

    /**
     * Runs a custom checker. Returns "AC" if exit code 0, "WA" otherwise.
     * Checker is called with: checker <input> <expected> <actual>
     */
    public String runChecker(String checkerBinPath, String inputPath, String expectedPath,
                             String actualOutput, String workDir) throws IOException {
        Path checkerDir = Path.of(checkerBinPath).getParent();
        Path actualFile = Path.of(workDir, "actual.txt");
        Files.writeString(actualFile, actualOutput);

        // Determine language from checker directory
        boolean isCpp = Files.exists(checkerDir.resolve("solution"));
        String image = isCpp
                ? judgeConfig.getLanguages().getOrDefault("cpp",
                    new JudgeConfig.LanguageConfig()).getImage()
                : judgeConfig.getLanguages().getOrDefault("java",
                    new JudgeConfig.LanguageConfig()).getImage();
        if (image == null) image = "gcc:13";

        String checkerCmd = isCpp
                ? "/checker/solution /input.txt /expected.txt /actual.txt"
                : "java -cp /checker Checker /input.txt /expected.txt /actual.txt";

        List<String> cmd = new ArrayList<>(List.of("docker", "run", "--rm"));
        cmd.addAll(baseSandboxFlags());
        cmd.addAll(List.of(
                "--memory", "256m",
                "--memory-swap", "256m",
                "--pids-limit", "64",
                "--cpus", "0.5",
                "-v", checkerDir.toString() + ":/checker:ro",
                "-v", inputPath + ":/input.txt:ro",
                "-v", expectedPath + ":/expected.txt:ro",
                "-v", actualFile.toString() + ":/actual.txt:ro",
                image,
                "/bin/sh", "-c", checkerCmd
        ));

        ProcessResult result = runProcess(cmd, 10_000);
        return result.exitCode() == 0 ? "AC" : "WA";
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
                "--tmpfs", "/tmp:size=64m"
        );
    }

    /** Sandbox runs as uid 1000; dirs created by the (root) API process must be opened up. */
    private static void makeWritableByAll(Path dir) {
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

    static boolean isDockerDaemonError(String text) {
        if (text == null || text.isBlank()) return false;
        return text.contains("Cannot connect to the Docker daemon")
                || text.contains("Is the docker daemon running")
                || text.contains("docker: not found")
                || text.contains("executable file not found");
    }

    private record ProcessResult(String stdout, String stderr, int exitCode, boolean timedOut) {}
}
