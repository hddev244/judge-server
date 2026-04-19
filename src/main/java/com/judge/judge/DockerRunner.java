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

        Files.writeString(workDir.resolve(lang.getSourceFile()), sourceCode);

        if (lang.getCompileCmd() == null || lang.getCompileCmd().isBlank()) {
            return CompileResult.builder().success(true).workDir(workDir.toString()).build();
        }

        List<String> cmd = buildDockerCmd(lang.getImage(), workDir.toString(), lang.getCompileCmd(), true);
        ProcessResult result = runProcess(cmd, 30_000);

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
        int memMb = Math.max(memoryKb / 1024, 64);
        int cpuSecs = Math.max(timeLimitMs / 1000 + 1, 2);
        String runCmd = lang.getRunCmd().replace("{mem}", String.valueOf(memMb));

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", memMb + "m",
                "--memory-swap", memMb + "m",
                "--cpus", "0.5",
                "--pids-limit", "64",
                "--ulimit", "cpu=" + cpuSecs + ":" + cpuSecs,
                "--read-only",
                "--tmpfs", "/tmp:size=64m",
                "-v", workDir + ":/code:ro",
                "-v", inputPath + ":/input.txt:ro",
                "-w", "/code",
                lang.getImage(),
                "/bin/sh", "-c",
                "timeout " + (timeLimitMs / 1000 + 1) + " " + runCmd + " < /input.txt"
        ));

        long start = System.currentTimeMillis();
        try {
            ProcessResult result = runProcess(cmd, timeLimitMs + 3000L);
            long elapsed = System.currentTimeMillis() - start;

            if (result.timedOut() || result.exitCode() == 124) return RunResult.tle(elapsed);
            if (result.exitCode() == 137) return RunResult.mle();

            if (isDockerDaemonError(result.stderr())) {
                log.error("Docker daemon unavailable during run, workDir={}: {}", workDir, result.stderr().trim());
                return RunResult.dockerUnavailable(result.stderr().trim());
            }

            return RunResult.builder()
                    .stdout(result.stdout())
                    .stderr(result.stderr())
                    .exitCode(result.exitCode())
                    .timeMs(elapsed)
                    .build();

        } catch (IOException e) {
            log.error("Docker run failed (binary unavailable?), workDir={}", workDir, e);
            return RunResult.dockerUnavailable(e.getMessage());
        }
    }

    /**
     * Compiles a custom checker. Returns the path to the checker binary, or throws on failure.
     */
    public String compileChecker(String language, String sourceCode, Long problemId) throws IOException {
        JudgeConfig.LanguageConfig lang = getLanguageConfig(language);
        Path checkerDir = Path.of(judgeConfig.getTestcaseBasePath(), String.valueOf(problemId), "checker");
        Files.createDirectories(checkerDir);

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

        List<String> cmd = List.of(
                "docker", "run", "--rm",
                "--network", "none",
                "--memory", "256m",
                "--cpus", "0.5",
                "-v", checkerDir.toString() + ":/checker:ro",
                "-v", inputPath + ":/input.txt:ro",
                "-v", expectedPath + ":/expected.txt:ro",
                "-v", actualFile.toString() + ":/actual.txt:ro",
                image,
                "/bin/sh", "-c", checkerCmd
        );

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

    private List<String> buildDockerCmd(String image, String workDir, String shellCmd, boolean rw) {
        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network", "none",
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

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread outReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                r.lines().forEach(l -> stdout.append(l).append("\n"));
            } catch (IOException ignored) {}
        });
        Thread errReader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                r.lines().forEach(l -> stderr.append(l).append("\n"));
            } catch (IOException ignored) {}
        });
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

        try { outReader.join(500); } catch (InterruptedException ignored) {}
        try { errReader.join(500); } catch (InterruptedException ignored) {}

        return new ProcessResult(stdout.toString(), stderr.toString(), process.exitValue(), false);
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
