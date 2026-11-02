package com.judge.judge;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class OutputComparator {

    /** Backwards-compatible exact comparison. */
    public boolean compare(String actualOutput, String expectedFilePath) {
        return compare(actualOutput, expectedFilePath, "EXACT", null);
    }

    /**
     * @param mode    "EXACT" (token/whitespace-normalized string match) or
     *                "FLOAT" (numeric tokens compared within epsilon, others exact)
     * @param epsilon absolute+relative tolerance for FLOAT mode (default 1e-6)
     */
    public boolean compare(String actualOutput, String expectedFilePath, String mode, Double epsilon) {
        try (BufferedReader er = Files.newBufferedReader(Path.of(expectedFilePath))) {
            List<String> expected = normalize(er);
            List<String> actual = normalize(actualOutput.lines().iterator());
            if ("FLOAT".equalsIgnoreCase(mode)) {
                return floatEquals(expected, actual, epsilon != null ? epsilon : 1e-6);
            }
            return expected.equals(actual);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Same rules as {@link #compare(String, String, String, Double)}, but streams both files line by
     * line so an answer of any size is compared in full without being held in memory.
     */
    public boolean compareFiles(String actualPath, String expectedPath, String mode, Double epsilon) {
        boolean isFloat = "FLOAT".equalsIgnoreCase(mode);
        double eps = epsilon != null ? epsilon : 1e-6;
        try (BufferedReader er = Files.newBufferedReader(Path.of(expectedPath), StandardCharsets.UTF_8);
             BufferedReader ar = Files.newBufferedReader(Path.of(actualPath), StandardCharsets.UTF_8)) {
            NormalizedLines expected = new NormalizedLines(er);
            NormalizedLines actual = new NormalizedLines(ar);
            while (true) {
                String e = expected.next();
                String a = actual.next();
                if (e == null || a == null) return e == null && a == null;
                if (isFloat ? !floatLineEquals(e, a, eps) : !e.equals(a)) return false;
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            // Undecodable bytes or an unreadable file can't match a text answer.
            return false;
        }
    }

    /**
     * Lines with trailing whitespace stripped, leading blank lines skipped and trailing blank lines
     * dropped — the streaming twin of {@link #normalize(java.util.Iterator)}.
     */
    private static final class NormalizedLines {
        private final BufferedReader reader;
        private boolean started;
        private int pendingBlanks;
        private String held;

        NormalizedLines(BufferedReader reader) {
            this.reader = reader;
        }

        /** Next normalized line, or null at the end. */
        String next() throws IOException {
            if (pendingBlanks > 0) {
                pendingBlanks--;
                return "";
            }
            if (held != null) {
                String line = held;
                held = null;
                return line;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                line = stripTrailing(line);
                if (line.isEmpty()) {
                    if (started) pendingBlanks++;
                    continue;
                }
                started = true;
                if (pendingBlanks > 0) {
                    held = line;
                    pendingBlanks--;
                    return "";
                }
                return line;
            }
            pendingBlanks = 0;
            return null;
        }
    }

    private boolean floatLineEquals(String e, String a, double eps) {
        return floatEquals(List.of(e), List.of(a), eps);
    }

    private boolean floatEquals(List<String> expected, List<String> actual, double eps) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            String[] et = expected.get(i).split("\\s+");
            String[] at = actual.get(i).split("\\s+");
            if (et.length != at.length) return false;
            for (int j = 0; j < et.length; j++) {
                if (et[j].equals(at[j])) continue;
                Double ev = parseDouble(et[j]);
                Double av = parseDouble(at[j]);
                if (ev == null || av == null) return false;
                double diff = Math.abs(ev - av);
                if (diff > eps && diff > eps * Math.abs(ev)) return false;
            }
        }
        return true;
    }

    private static Double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private List<String> normalize(BufferedReader reader) throws IOException {
        return normalize(reader.lines().iterator());
    }

    /** Strips trailing whitespace per line and leading/trailing blank lines. */
    private List<String> normalize(java.util.Iterator<String> lines) {
        List<String> out = new ArrayList<>();
        while (lines.hasNext()) out.add(stripTrailing(lines.next()));
        int start = 0, end = out.size();
        while (start < end && out.get(start).isBlank()) start++;
        while (end > start && out.get(end - 1).isBlank()) end--;
        return new ArrayList<>(out.subList(start, end));
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
