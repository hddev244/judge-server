package com.judge.judge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component("docker")
public class DockerHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DockerHealthIndicator.class);

    @Override
    public Health health() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Health.down().withDetail("error", "docker info timed out after 3s").build();
            }
            if (process.exitValue() == 0) {
                return Health.up().build();
            }
            return Health.down().withDetail("error", "docker info exited with code " + process.exitValue()).build();
        } catch (IOException e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", "interrupted").build();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        Health h = health();
        if (!h.getStatus().equals(org.springframework.boot.actuate.health.Status.UP)) {
            log.warn("Docker daemon is NOT available on startup: {}. Submissions will fail with SE verdict.",
                    h.getDetails());
        } else {
            log.info("Docker daemon is available.");
        }
    }
}
