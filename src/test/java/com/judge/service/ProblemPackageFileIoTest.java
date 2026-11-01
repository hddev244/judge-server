package com.judge.service;

import com.judge.api.dto.ProblemPackage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProblemPackageFileIoTest {

    private static final List<ProblemPackage.TestCaseSpec> TESTS =
            List.of(new ProblemPackage.TestCaseSpec("1 2\n", "3\n", false, 100, 0, null));

    @Test
    void stdioPackageJsonHasNoFileIoKeys_soExistingHashesStayStable() throws Exception {
        ProblemPackage pkg = new ProblemPackage("A+B", "d", "MARKDOWN", 1000, 262144, "easy",
                List.of(), "EXACT", null, "ALL", null, null, List.of(), TESTS);

        String json = ProblemPackageService.CANONICAL.writeValueAsString(pkg);
        assertFalse(json.contains("ioMode"));
        assertFalse(json.contains("inputFile"));
        assertFalse(json.contains("outputFile"));
    }

    @Test
    void filePackageCarriesNamesAndHashesDifferently() throws Exception {
        ProblemPackage stdio = new ProblemPackage("A+B", "d", "MARKDOWN", 1000, 262144, "easy",
                List.of(), "EXACT", null, "ALL", null, null, List.of(), TESTS);
        ProblemPackage file = new ProblemPackage("A+B", "d", "MARKDOWN", 1000, 262144, "easy",
                List.of(), "EXACT", null, "ALL", null, null, List.of(), TESTS,
                "FILE", "BAI1.INP", "BAI1.OUT");

        String json = ProblemPackageService.CANONICAL.writeValueAsString(file);
        assertTrue(json.contains("\"inputFile\":\"BAI1.INP\""));
        assertTrue(json.contains("\"ioMode\":\"FILE\""));
        assertNotEquals(ProblemPackageService.hash(stdio), ProblemPackageService.hash(file));
    }
}
