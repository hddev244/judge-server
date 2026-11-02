package com.judge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ── compareFiles (streaming, used for judging) ──

    private boolean files(Path dir, String expected, String actual, String mode) throws IOException {
        Path e = dir.resolve("e.txt");
        Path a = dir.resolve("a.txt");
        Files.writeString(e, expected);
        Files.writeString(a, actual);
        return comparator.compareFiles(a.toString(), e.toString(), mode, null);
    }

    @Test
    void compareFiles_agreesWithStringCompare(@TempDir Path dir) throws IOException {
        String[][] cases = {
                {"1 2\n3\n", "1 2\n3\n"},
                {"1 2\n3\n", "1 2   \n3\n\n\n"},           // trailing spaces / blank lines
                {"1\n3\n", "\n\n1\n3"},                       // leading blank lines
                {"a\n\nb\n", "a\n\nb"},                         // blank line in the middle kept
                {"a\n\nb\n", "a\nb\n"},                         // ...and required
                {"a\n\n\nb\n", "a\n\nb\n"},
                {"1\n2\n", "1\n2\n3\n"},                      // extra line
                {"1\n2\n3\n", "1\n2\n"},                      // missing line
                {"", "\n\n"},
                {"x\r\ny\r\n", "x\ny\n"},                     // CRLF
                {"", "something"},
        };
        for (String[] c : cases) {
            Path e = dir.resolve("exp.txt");
            Files.writeString(e, c[0]);
            boolean expected = comparator.compare(c[1], e.toString(), "EXACT", null);
            assertEquals(expected, files(dir, c[0], c[1], "EXACT"),
                    () -> "mismatch for expected=" + c[0].replace("\n", "\\n") + " actual=" + c[1].replace("\n", "\\n"));
        }
    }

    @Test
    void compareFiles_floatMode(@TempDir Path dir) throws IOException {
        assertTrue(files(dir, "3.14159 2\n", "3.1415900001 2\n", "FLOAT"));
        assertFalse(files(dir, "3.14\n", "3.15\n", "FLOAT"));
        assertFalse(files(dir, "1 2\n", "1\n", "FLOAT"));
    }

    @Test
    void compareFiles_judgesAnswersLongerThanTheDisplayCap(@TempDir Path dir) throws IOException {
        // Regression: problem 432 test 4735 — 100 000 lines of 1000000000 (1.1 MB) — was cut at the
        // 1 MB display cap and judged WA although the solution printed the right answer.
        String big = "1000000000\n".repeat(100_000);
        assertTrue(big.length() > 1_048_576);
        assertTrue(files(dir, big, big, "EXACT"));
        assertFalse(files(dir, big, big.substring(0, 1_048_576), "EXACT"));
        assertFalse(files(dir, big, big + "1\n", "EXACT"));
    }
}
