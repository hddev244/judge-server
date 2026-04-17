package com.judge.judge;

import com.judge.judge.model.CompileResult;
import com.judge.judge.model.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DockerRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRunner.class);

    private static final String WORK_BASE = "/tmp/judge";

    private static final Map<String, String> IMAGES = Map.of(
            "cpp",    "gcc:13",
            "java",   "eclipse-temurin:21",
            "python", "python:3.12-slim"
    );

    private static final Map<String, String> SOURCE_FILES = Map.of(
            "cpp",    "solution.cpp",
            "java",   "Solution.java",
            "python", "solution.py"
    );

    public CompileResult compile(String language, String sourceCode, String jobId) throws IOException {
        Path workDir = Path.of(WORK_BASE, jobId);
        Files.createDirectories(workDir);

        String sourceFile = SOURCE_FILES.get(language);
        Files.writeString(workDir.resolve(sourceFile), sourceCode);

        if ("python".equals(language)) {
            return CompileResult.builder()
                    .success(true)
                    .workDir(workDir.toString())
                    .build();
        }

        List<String> cmd = buildCompileCommand(language, workDir.toString(), sourceFile);
        ProcessResult result = runProcess(cmd, 30_000);

        if (result.exitCode() != 0) {
            return CompileResult.builder()
                    .success(false)
                    .workDir(workDir.toString())
                    .errorOutput(result.stderr().isBlank() ? result.stdout() : result.stderr())
                    .build();
        }

        return CompileResult.builder()
                .success(true)
                .workDir(workDir.toString())
                .build();
    }

    public RunResult run(String workDir, String language, String inputPath,
                         int timeLimitMs, int memoryKb) {
        int memMb = Math.max(memoryKb / 1024, 64);
        int cpuSecs = Math.max(timeLimitMs / 1000 + 1, 2);
        String image = IMAGES.get(language);
        String runCmd = buildRunCommand(language, memMb);

        List<String> cmd = List.of(
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
                image,
                "/bin/sh", "-c",
                "timeout " + (timeLimitMs / 1000 + 1) + " " + runCmd + " < /input.txt"
        );

        long start = System.currentTimeMillis();
        try {
            ProcessResult result = runProcess(cmd, timeLimitMs + 3000L);
            long elapsed = System.currentTimeMillis() - start;

            if (result.timedOut() || result.exitCode() == 124) {
                return RunResult.tle(elapsed);
            }
            // OOM killed by Docker
            if (result.exitCode() == 137) {
                return RunResult.mle();
            }

            return RunResult.builder()
                    .stdout(result.stdout())
                    .stderr(result.stderr())
                    .exitCode(result.exitCode())
                    .timeMs(elapsed)
                    .build();

        } catch (IOException e) {
            log.error("Docker run failed for workDir={}", workDir, e);
            return RunResult.builder()
                    .exitCode(1)
                    .stderr(e.getMessage())
                    .stdout("")
                    .build();
        }
    }

    public void cleanup(String jobId) {
        Path workDir = Path.of(WORK_BASE, jobId);
        try {
            deleteRecursively(workDir);
        } catch (IOException e) {
            log.warn("Failed to cleanup workDir={}", workDir, e);
        }
    }

    private List<String> buildCompileCommand(String language, String workDir, String sourceFile) {
        String image = IMAGES.get(language);
        return switch (language) {
            case "cpp" -> List.of(
                    "docker", "run", "--rm",
                    "--network", "none",
                    "-v", workDir + ":/code",
                    "-w", "/code",
                    image,
                    "g++", "-O2", "-o", "solution", sourceFile
            );
            case "java" -> List.of(
                    "docker", "run", "--rm",
                    "--network", "none",
                    "-v", workDir + ":/code",
                    "-w", "/code",
                    image,
                    "javac", "-encoding", "UTF-8", sourceFile
            );
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private String buildRunCommand(String language, int memMb) {
        return switch (language) {
            case "cpp"    -> "./solution";
            case "java"   -> "java -cp /code -Xmx" + memMb + "m Solution";
            case "python" -> "python3 solution.py";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private ProcessResult runProcess(List<String> cmd, long timeoutMs) throws IOException {
        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start();

        // Read stdout and stderr concurrently to avoid blocking
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

    private record ProcessResult(String stdout, String stderr, int exitCode, boolean timedOut) {}
}
