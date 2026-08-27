package com.judge.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionPersistenceServiceTest {

    @Test
    void clip_nullAndEmptyBecomeNull() {
        assertNull(SubmissionPersistenceService.clip(null));
        assertNull(SubmissionPersistenceService.clip(""));
    }

    @Test
    void clip_shortStringUnchanged() {
        assertEquals("hello\nworld", SubmissionPersistenceService.clip("hello\nworld"));
    }

    @Test
    void clip_longStringTruncated() {
        String s = "x".repeat(SubmissionPersistenceService.STDOUT_CAP + 50);
        String clipped = SubmissionPersistenceService.clip(s);
        assertNotNull(clipped);
        assertTrue(clipped.endsWith("\n...[truncated]"));
        assertEquals(SubmissionPersistenceService.STDOUT_CAP + "\n...[truncated]".length(), clipped.length());
    }
}
