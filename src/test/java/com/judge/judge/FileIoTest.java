package com.judge.judge;

import com.judge.exception.JudgeException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileIoTest {

    @Test
    void normalize_noNamesIsStdio() {
        assertEquals(FileIo.Spec.NONE, FileIo.normalize(null, null, null, null));
        assertEquals(FileIo.Spec.NONE, FileIo.normalize("", " ", "", "EXACT"));
    }

    @Test
    void normalize_stdioDropsNames() {
        assertEquals(FileIo.Spec.NONE, FileIo.normalize("STDIO", "BAI1.INP", "BAI1.OUT", null));
    }

    @Test
    void normalize_namesWithoutModeMeanStrictFile() {
        var spec = FileIo.normalize(null, " BAI1.INP ", "BAI1.OUT", null);
        assertEquals(new FileIo.Spec("FILE", "BAI1.INP", "BAI1.OUT"), spec);
        assertTrue(spec.isFile());
        assertTrue(spec.isStrict());
    }

    @Test
    void normalize_lenientMode() {
        var spec = FileIo.normalize("file_or_stdio", "sum.inp", "sum.out", "CUSTOM");
        assertEquals("FILE_OR_STDIO", spec.mode());
        assertTrue(spec.isFile());
        assertFalse(spec.isStrict());
    }

    @Test
    void normalize_rejectsInvalidCombinations() {
        assertThrows(JudgeException.class, () -> FileIo.normalize("FILE", "BAI1.INP", null, null));
        assertThrows(JudgeException.class, () -> FileIo.normalize("PIPE", "a.inp", "a.out", null));
        assertThrows(JudgeException.class, () -> FileIo.normalize("FILE", "bai1.txt", "BAI1.TXT", null));
        assertThrows(JudgeException.class, () -> FileIo.normalize("FILE", "a.inp", "a.out", "INTERACTIVE"));
    }

    @Test
    void normalize_rejectsPathsAndOddNames() {
        for (String bad : new String[]{"../x.inp", "/etc/passwd", "dir/a.inp", ".hidden", "a b.inp",
                "a'b.inp", "a\"b", "$(id)", "x".repeat(65)}) {
            assertThrows(JudgeException.class, () -> FileIo.normalize("FILE", bad, "ok.out", null), bad);
        }
    }

    @Test
    void absoluteRunCmd_pointsRelativeProgramAndSourceIntoCode() {
        assertEquals("/code/solution", FileIo.absoluteRunCmd("./solution", "solution.cpp"));
        assertEquals("python3 /code/solution.py", FileIo.absoluteRunCmd("python3 solution.py", "solution.py"));
        String java = "java -XX:+TieredCompilation -Xss64m -cp /code -Xmx256m Solution";
        assertEquals(java, FileIo.absoluteRunCmd(java, "Solution.java"));
    }

    @Test
    void aliases_coverOtherCasings() {
        assertEquals(Set.of("bai1.inp"), FileIo.aliases("BAI1.INP"));
        assertEquals(Set.of("bai1.inp", "BAI1.INP"), FileIo.aliases("Bai1.inp"));
    }

    @Test
    void batchCaseScript_strictHidesStdinAndIgnoresStdout() {
        String s = FileIo.batchCaseScript(new FileIo.Spec("FILE", "BAI1.INP", "BAI1.OUT"),
                7, "/tests/7.in", "2", "/code/solution");
        assertTrue(s.contains("cp \"/tests/7.in\" \"/box/7/BAI1.INP\""));
        assertTrue(s.contains("cd /box/7 && /code/solution < \"/dev/null\" > \"/dev/null\""));
        assertTrue(s.contains("grep -ixF -- \"BAI1.OUT\""));
        assertTrue(s.contains(": > /metrics/7.nofile"));
        assertFalse(s.contains(".stdout"));
    }

    @Test
    void batchCaseScript_lenientFallsBackToStdout() {
        String s = FileIo.batchCaseScript(new FileIo.Spec("FILE_OR_STDIO", "a.inp", "a.out"),
                3, "/tests/3.in", "2", "/code/solution");
        assertTrue(s.contains("< \"/box/3/a.inp\" > \"/metrics/3.stdout\""));
        assertTrue(s.contains("mv /metrics/3.stdout /metrics/3.out"));
    }
}
