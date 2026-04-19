package com.judge.judge;

import com.judge.config.JudgeConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class WorkDirCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(WorkDirCleanupJob.class);

    private final Path workBase;

    public WorkDirCleanupJob(JudgeConfig judgeConfig, MeterRegistry meterRegistry) {
        this.workBase = Path.of(judgeConfig.getWorkBase());
        Gauge.builder("judge.workdir.count", this, WorkDirCleanupJob::countWorkDirs)
                .description("Number of active work directories under judge work base")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void cleanupStaleWorkDirs() {
        if (!Files.exists(workBase)) return;
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        int deleted = 0;

        List<Path> dirs;
        try (Stream<Path> stream = Files.list(workBase)) {
            dirs = stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.warn("Cannot list workBase={}: {}", workBase, e.getMessage());
            return;
        }

        for (Path dir : dirs) {
            try {
                FileTime lastModified = Files.getLastModifiedTime(dir);
                if (lastModified.toInstant().isBefore(cutoff)) {
                    deleteRecursively(dir);
                    deleted++;
                }
            } catch (IOException e) {
                log.warn("Failed to clean stale workDir={}: {}", dir, e.getMessage());
            }
        }

        if (deleted > 0) {
            log.info("Cleaned {} stale work directories (older than 1h) from {}", deleted, workBase);
        }
    }

    private double countWorkDirs() {
        if (!Files.exists(workBase)) return 0;
        try (Stream<Path> stream = Files.list(workBase)) {
            return stream.filter(Files::isDirectory).count();
        } catch (IOException e) {
            return -1;
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
