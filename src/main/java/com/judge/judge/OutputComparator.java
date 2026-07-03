package com.judge.judge;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
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
