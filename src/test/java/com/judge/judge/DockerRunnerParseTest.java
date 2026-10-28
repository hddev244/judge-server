package com.judge.judge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockerRunnerParseTest {

    @Test
    void parsesGnuTimeLine() {
        var m = DockerRunner.parseMetrics("0.53 0.40 0.12 20480 0");
        assertNotNull(m);
        assertEquals(530, m.wallTimeMs());
        assertEquals(520, m.cpuTimeMs());   // (0.40 + 0.12) * 1000
        assertEquals(20480, m.maxRssKb());
        assertEquals(0, m.exitStatus());
    }

    @Test
    void usesLastLineWhenSignalMessagePrepended() {
        var m = DockerRunner.parseMetrics("Command terminated by signal 9\n0.10 0.05 0.02 65536 137");
        assertNotNull(m);
        assertEquals(70, m.cpuTimeMs());
        assertEquals(65536, m.maxRssKb());
        assertEquals(137, m.exitStatus());
    }

    @Test
    void returnsNullOnMalformed() {
        assertNull(DockerRunner.parseMetrics(null));
        assertNull(DockerRunner.parseMetrics(""));
        assertNull(DockerRunner.parseMetrics("garbage line"));
    }

    @Test
    void dockerDaemonErrorAnchoredToCliLines() {
        assertTrue(DockerRunner.isDockerDaemonError(
                "Cannot connect to the Docker daemon at unix:///var/run/docker.sock"));
        assertTrue(DockerRunner.isDockerDaemonError("docker: command not found"));
    }

    @Test
    void userStderrIsNotMistakenForDaemonError() {
        // A solution printing docker-ish words must not be classified as SE.
        assertFalse(DockerRunner.isDockerDaemonError(
                "my program says: executable file not found in the maze"));
        assertFalse(DockerRunner.isDockerDaemonError("runtime error: index out of range"));
    }

    @Test
    void resolveContainerPath_transformsWorkDirToCode() {
        assertEquals("/code/custom.in",
                DockerRunner.resolveContainerPath("/tmp/judge/job123/custom.in", "/tmp/judge/job123"));
        assertEquals("/data/problems/194/cases/1.in",
                DockerRunner.resolveContainerPath("/data/problems/194/cases/1.in", "/tmp/judge/job123"));
        assertEquals("", DockerRunner.resolveContainerPath(null, "/tmp/judge/job123"));
    }

    @Test
    void inputMounts_mountsEachStoredInputAloneNeverTheTestcaseDir() {
        var mounts = DockerRunner.inputMounts(List.of(
                new DockerRunner.BatchTestCase(41L, "/data/problems/7/cases/41.in"),
                new DockerRunner.BatchTestCase(42L, "/data/problems/7/cases/42.in")),
                "/tmp/judge/job123");

        assertEquals("/tests/41.in", mounts.containerPaths().get(41L));
        assertEquals("/tests/42.in", mounts.containerPaths().get(42L));
        // The expected outputs live next to the inputs; only the input files themselves are mounted.
        assertEquals(List.of(
                "-v", "/data/problems/7/cases/41.in:/tests/41.in:ro",
                "-v", "/data/problems/7/cases/42.in:/tests/42.in:ro"), mounts.volumeArgs());
    }

    @Test
    void inputMounts_customInputInsideWorkDirUsesCodeMount() {
        var mounts = DockerRunner.inputMounts(List.of(
                new DockerRunner.BatchTestCase(1L, "/tmp/judge/job123/custom.in"),
                new DockerRunner.BatchTestCase(2L, "")),
                "/tmp/judge/job123");

        assertEquals("/code/custom.in", mounts.containerPaths().get(1L));
        assertEquals("/dev/null", mounts.containerPaths().get(2L));
        assertTrue(mounts.volumeArgs().isEmpty());
    }

    @Test
    void inputMounts_siblingJobDirIsNotTreatedAsWorkDir() {
        var mounts = DockerRunner.inputMounts(List.of(
                new DockerRunner.BatchTestCase(1L, "/tmp/judge/job1234/custom.in")),
                "/tmp/judge/job123");

        assertEquals("/tests/1.in", mounts.containerPaths().get(1L));
    }
}
