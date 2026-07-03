package com.judge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    private Path write(Path dir, String content) throws IOException {
        Path f = dir.resolve("expected.txt");
        Files.writeString(f, content);
        return f;
    }

    @Test
    void exactMatch(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "hello\nworld\n");
        assertTrue(comparator.compare("hello\nworld\n", exp.toString()));
    }

    @Test
    void ignoresTrailingWhitespaceAndBlankLines(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "1 2 3\n\n");
        assertTrue(comparator.compare("1 2 3   \n", exp.toString()));
    }

    @Test
    void detectsMismatch(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "42\n");
        assertFalse(comparator.compare("43\n", exp.toString()));
    }

    @Test
    void floatWithinEpsilonIsAccepted(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "3.141593\n");
        assertTrue(comparator.compare("3.1415926\n", exp.toString(), "FLOAT", 1e-4));
    }

    @Test
    void floatOutsideEpsilonIsRejected(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "1.0\n");
        assertFalse(comparator.compare("1.5\n", exp.toString(), "FLOAT", 1e-6));
    }

    @Test
    void floatModeStillRejectsDifferentTokenCount(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "1.0 2.0\n");
        assertFalse(comparator.compare("1.0\n", exp.toString(), "FLOAT", 1e-6));
    }

    @Test
    void sameValueUnderExactStillMatchesInFloatMode(@TempDir Path dir) throws IOException {
        Path exp = write(dir, "abc 1.0\n");
        assertTrue(comparator.compare("abc 1.0\n", exp.toString(), "FLOAT", 1e-6));
    }
}
