package com.judge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.judge.api.dto.ProblemPackage;
import com.judge.domain.ApiKey;
import com.judge.domain.ApiKeyRole;
import com.judge.domain.Problem;
import com.judge.exception.JudgeException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProblemPackageServiceTest {

    private static ProblemPackage pkg(List<ProblemPackage.SubtaskSpec> subtasks,
                                      List<ProblemPackage.TestCaseSpec> tests) {
        return new ProblemPackage("A+B", "Tính tổng", "MARKDOWN", 1000, 262144, "easy",
                List.of("math"), "EXACT", null, "ALL", null, null, subtasks, tests);
    }

    private static ProblemPackage.TestCaseSpec tc(String in, String out, int score, String subtask) {
        return new ProblemPackage.TestCaseSpec(in, out, false, score, 0, subtask);
    }

    @Test
    void hashSurvivesJsonRoundTrip() throws Exception {
        ProblemPackage original = pkg(
                List.of(new ProblemPackage.SubtaskSpec("st1", "Ví dụ", 0, 0)),
                List.of(tc("1 2\n", "3\n", 7, "st1"), tc("2 2\n", "4\n", 93, null)));

        ObjectMapper wire = new ObjectMapper();
        ProblemPackage received = wire.readValue(wire.writeValueAsBytes(original), ProblemPackage.class);

        assertEquals(ProblemPackageService.hash(original), ProblemPackageService.hash(received));
    }

    @Test
    void hashChangesWhenAScoreChanges() {
        ProblemPackage a = pkg(List.of(), List.of(tc("1", "1", 10, null)));
        ProblemPackage b = pkg(List.of(), List.of(tc("1", "1", 11, null)));
        assertNotEquals(ProblemPackageService.hash(a), ProblemPackageService.hash(b));
    }

    @Test
    void rejectsUnknownSubtaskReference() {
        ProblemPackage p = pkg(List.of(), List.of(tc("1", "1", 1, "missing")));
        assertThrows(JudgeException.class, () -> ProblemPackageService.validateStructure(p));
    }

    @Test
    void rejectsDuplicateSubtaskKeys() {
        ProblemPackage p = pkg(
                List.of(new ProblemPackage.SubtaskSpec("s", "A", 50, 0), new ProblemPackage.SubtaskSpec("s", "B", 50, 1)),
                List.of(tc("1", "1", 0, "s")));
        assertThrows(JudgeException.class, () -> ProblemPackageService.validateStructure(p));
    }

    @Test
    void externalKeyMustBeSafe() {
        assertDoesNotThrow(() -> ProblemPackageService.validateExternalKey("65f1a2b3c4d5e6f7a8b9c0d1"));
        assertThrows(JudgeException.class, () -> ProblemPackageService.validateExternalKey("../etc"));
        assertThrows(JudgeException.class, () -> ProblemPackageService.validateExternalKey(""));
        assertThrows(JudgeException.class, () -> ProblemPackageService.validateExternalKey(null));
    }

    @Test
    void managedSlugCannotCollideWithAdminSlugs() {
        ApiKey key = ApiKey.builder().id(7L).role(ApiKeyRole.GRADER).build();
        String slug = ProblemPackageService.managedSlug(key, "abc123");
        assertEquals("g7_abc123", slug);
        assertFalse(slug.matches("[a-z0-9-]+"), "admin-authored slugs are [a-z0-9-]+");
    }

    @Test
    void ownershipRequiresSameKeyAndExternalKey() {
        ApiKey owner = ApiKey.builder().id(1L).role(ApiKeyRole.GRADER).build();
        ApiKey other = ApiKey.builder().id(2L).role(ApiKeyRole.GRADER).build();
        Problem managed = Problem.builder().externalKey("x").ownerKeyId(1L).build();
        Problem local = Problem.builder().ownerKeyId(1L).build();

        assertTrue(ProblemPackageService.isOwnedBy(managed, owner));
        assertFalse(ProblemPackageService.isOwnedBy(managed, other));
        assertFalse(ProblemPackageService.isOwnedBy(local, owner));
    }
}
