package com.judge.service;

import com.judge.api.dto.ProblemResponse;
import com.judge.domain.Problem;
import com.judge.domain.Subtask;
import com.judge.domain.TestCase;
import com.judge.exception.JudgeException;
import com.judge.judge.DockerRunner;
import com.judge.repository.ProblemRepository;
import com.judge.repository.SubtaskRepository;
import com.judge.repository.TestCaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProblemImportService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubtaskRepository subtaskRepository;
    private final DockerRunner dockerRunner;
    private final String basePath;

    public ProblemImportService(ProblemRepository problemRepository,
                                 TestCaseRepository testCaseRepository,
                                 SubtaskRepository subtaskRepository,
                                 DockerRunner dockerRunner,
                                 @Value("${judge.testcase.base-path}") String basePath) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.subtaskRepository = subtaskRepository;
        this.dockerRunner = dockerRunner;
        this.basePath = basePath;
    }

    @Transactional
    public ProblemResponse importZip(InputStream zipStream) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(normalizePath(entry.getName()), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }

        // ── problem.yml ─────────────────────────────────────────────────────
        byte[] problemYaml = files.get("problem.yml");
        if (problemYaml == null) throw JudgeException.badRequest("ZIP must contain problem.yml");

        Map<String, Object> meta = new Yaml().load(new String(problemYaml));
        String slug = getString(meta, "slug");
        String title = getString(meta, "title");
        if (slug == null || title == null) throw JudgeException.badRequest("problem.yml must have slug and title");

        if (problemRepository.findBySlug(slug).isPresent()) {
            throw JudgeException.badRequest("Slug already exists: " + slug);
        }

        Problem problem = Problem.builder()
                .slug(slug)
                .title(title)
                .description(getString(meta, "description"))
                .timeLimitMs(getInt(meta, "timeLimitMs", 2000))
                .memoryLimitKb(getInt(meta, "memoryLimitKb", 262144))
                .isPublished(false)
                .build();
        problem = problemRepository.save(problem);

        // ── test cases ───────────────────────────────────────────────────────
        Map<String, byte[]> inputs = new LinkedHashMap<>();
        Map<String, byte[]> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("tests/") && key.endsWith(".in")) {
                inputs.put(key.substring("tests/".length(), key.length() - 3), e.getValue());
            } else if (key.startsWith("tests/") && key.endsWith(".out")) {
                outputs.put(key.substring("tests/".length(), key.length() - 4), e.getValue());
            }
        }

        // ── subtasks.yml (optional) ──────────────────────────────────────────
        Map<String, Long> testToSubtask = new HashMap<>();
        byte[] subtasksYaml = files.get("subtasks.yml");
        if (subtasksYaml != null) {
            List<Map<String, Object>> subtaskList = new Yaml().load(new String(subtasksYaml));
            if (subtaskList != null) {
                int orderIdx = 0;
                for (Map<String, Object> stMeta : subtaskList) {
                    Subtask subtask = Subtask.builder()
                            .problem(problem)
                            .name(getString(stMeta, "name"))
                            .score(getInt(stMeta, "score", 0))
                            .orderIndex(orderIdx++)
                            .build();
                    subtask = subtaskRepository.save(subtask);

                    Object testsObj = stMeta.get("tests");
                    if (testsObj instanceof List<?> tests) {
                        for (Object t : tests) {
                            testToSubtask.put(String.valueOf(t), subtask.getId());
                        }
                    }
                }
            }
        }

        // ── save test case files ─────────────────────────────────────────────
        Path casesDir = Path.of(basePath, String.valueOf(problem.getId()), "cases");
        Files.createDirectories(casesDir);

        List<String> sortedNames = new ArrayList<>(inputs.keySet());
        Collections.sort(sortedNames);
        int orderIndex = 0;
        for (String name : sortedNames) {
            if (!outputs.containsKey(name)) continue;

            Subtask subtask = null;
            Long subtaskId = testToSubtask.get(name);
            if (subtaskId != null) {
                subtask = subtaskRepository.findById(subtaskId).orElse(null);
            }

            boolean isSample = name.startsWith("sample") || name.startsWith("ex");

            TestCase tc = TestCase.builder()
                    .problem(problem)
                    .subtask(subtask)
                    .inputPath("")
                    .outputPath("")
                    .isSample(isSample)
                    .score(subtask == null ? 1 : 0)
                    .orderIndex(orderIndex++)
                    .build();
            tc = testCaseRepository.save(tc);

            Path inPath = casesDir.resolve(tc.getId() + ".in");
            Path outPath = casesDir.resolve(tc.getId() + ".out");
            Files.write(inPath, inputs.get(name));
            Files.write(outPath, outputs.get(name));
            tc.setInputPath(inPath.toString());
            tc.setOutputPath(outPath.toString());
            testCaseRepository.save(tc);
        }

        // ── checker.cpp (optional) ────────────────────────────────────────────
        byte[] checkerSrc = files.get("checker.cpp");
        if (checkerSrc == null) checkerSrc = files.get("checker/checker.cpp");
        if (checkerSrc != null) {
            try {
                String binPath = dockerRunner.compileChecker("cpp", new String(checkerSrc), problem.getId());
                problem.setCheckerType("CUSTOM");
                problem.setCheckerLanguage("cpp");
                problem.setCheckerSource(new String(checkerSrc));
                problem.setCheckerBinPath(binPath);
                problemRepository.save(problem);
            } catch (IOException e) {
                // Checker compile failure is non-fatal; log warning
            }
        }

        return ProblemResponse.from(problem);
    }

    private String normalizePath(String name) {
        // Strip leading directory component if present (e.g., "archive/problem.yml" -> "problem.yml")
        int slash = name.indexOf('/');
        if (slash > 0 && !name.substring(0, slash).equals("tests") &&
                !name.substring(0, slash).equals("checker")) {
            String stripped = name.substring(slash + 1);
            if (!stripped.isEmpty()) return stripped;
        }
        return name;
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
