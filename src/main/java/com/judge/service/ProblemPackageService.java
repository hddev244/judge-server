package com.judge.service;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.judge.api.dto.ManagedProblemResponse;
import com.judge.api.dto.ProblemPackage;
import com.judge.domain.ApiKey;
import com.judge.domain.Problem;
import com.judge.domain.ProblemTag;
import com.judge.domain.Subtask;
import com.judge.domain.TestCase;
import com.judge.exception.JudgeException;
import com.judge.judge.DockerRunner;
import com.judge.repository.ProblemRepository;
import com.judge.repository.ProblemTagRepository;
import com.judge.repository.SubtaskRepository;
import com.judge.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ProblemPackageService {

    private static final Logger log = LoggerFactory.getLogger(ProblemPackageService.class);

    static final long MAX_PACKAGE_CHARS = 64L * 1024 * 1024;
    private static final Pattern EXTERNAL_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** Deterministic JSON so the source judge and every replica hash identical content identically. */
    static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private final ProblemRepository problemRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubtaskRepository subtaskRepository;
    private final ProblemService problemService;
    private final DockerRunner dockerRunner;
    private final String basePath;

    public ProblemPackageService(ProblemRepository problemRepository,
                                 ProblemTagRepository problemTagRepository,
                                 TestCaseRepository testCaseRepository,
                                 SubtaskRepository subtaskRepository,
                                 ProblemService problemService,
                                 DockerRunner dockerRunner,
                                 @Value("${judge.testcase-base-path}") String basePath) {
        this.problemRepository = problemRepository;
        this.problemTagRepository = problemTagRepository;
        this.testCaseRepository = testCaseRepository;
        this.subtaskRepository = subtaskRepository;
        this.problemService = problemService;
        this.dockerRunner = dockerRunner;
        this.basePath = basePath;
    }

    // ── Export (admin, source judge) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProblemPackage export(Long problemId) {
        Problem p = problemRepository.findById(problemId)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + problemId));

        List<Subtask> subtasks = subtaskRepository.findByProblemIdOrderByOrderIndexAsc(problemId);
        List<ProblemPackage.SubtaskSpec> subtaskSpecs = subtasks.stream()
                .map(st -> new ProblemPackage.SubtaskSpec(
                        "st" + st.getId(), st.getName(), st.getScore(), st.getOrderIndex()))
                .toList();

        List<ProblemPackage.TestCaseSpec> tcSpecs = new ArrayList<>();
        for (TestCase tc : testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId)) {
            tcSpecs.add(new ProblemPackage.TestCaseSpec(
                    readFile(tc.getInputPath()),
                    readFile(tc.getOutputPath()),
                    tc.isSample(),
                    tc.getScore(),
                    tc.getOrderIndex(),
                    tc.getSubtask() != null ? "st" + tc.getSubtask().getId() : null));
        }

        ProblemPackage.Checker checker = null;
        if (("CUSTOM".equals(p.getCheckerType()) || "INTERACTIVE".equals(p.getCheckerType()))
                && p.getCheckerSource() != null) {
            checker = new ProblemPackage.Checker(p.getCheckerType(), p.getCheckerLanguage(), p.getCheckerSource());
        }

        List<String> tags = p.getTags() == null ? List.of()
                : p.getTags().stream().map(ProblemTag::getTag).toList();

        return new ProblemPackage(
                p.getTitle(), p.getDescription(), p.getDescriptionFormat(),
                p.getTimeLimitMs(), p.getMemoryLimitKb(), p.getDifficulty(), tags,
                p.getComparisonMode(), p.getFloatEpsilon(), p.getJudgingMode(), p.getAllowedLanguages(),
                checker, subtaskSpecs, tcSpecs);
    }

    // ── Grader (replica judge) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ManagedProblemResponse get(ApiKey owner, String externalKey) {
        return ManagedProblemResponse.from(findOwned(owner, externalKey), false);
    }

    /**
     * Creates or fully replaces the problem identified by {@code (owner, externalKey)}. Identical
     * content (same hash) is a no-op so repeated syncs never churn test files or checker binaries.
     */
    @Transactional
    public ManagedProblemResponse upsert(ApiKey owner, String externalKey, ProblemPackage pkg) {
        validateExternalKey(externalKey);
        validateStructure(pkg);
        String hash = hash(pkg);

        Optional<Problem> existing = problemRepository.findByOwnerKeyIdAndExternalKey(owner.getId(), externalKey);
        if (existing.isPresent() && hash.equals(existing.get().getPackageHash())) {
            return ManagedProblemResponse.from(existing.get(), false);
        }

        Problem problem = existing.orElseGet(() -> Problem.builder()
                .slug(managedSlug(owner, externalKey))
                .externalKey(externalKey)
                .ownerKeyId(owner.getId())
                .status("PRIVATE")
                .build());
        if (problem.getId() != null) {
            clearGradingData(problem);
        }

        problem.setTitle(pkg.title());
        problem.setDescription(pkg.description());
        problem.setDescriptionFormat(pkg.descriptionFormat() != null ? pkg.descriptionFormat() : "MARKDOWN");
        problem.setTimeLimitMs(pkg.timeLimitMs());
        problem.setMemoryLimitKb(pkg.memoryLimitKb());
        problem.setDifficulty(pkg.difficulty());
        problem.setComparisonMode(pkg.comparisonMode() != null ? pkg.comparisonMode() : "EXACT");
        problem.setFloatEpsilon(pkg.floatEpsilon());
        problem.setJudgingMode(pkg.judgingMode() != null ? pkg.judgingMode() : "ALL");
        problem.setAllowedLanguages(pkg.allowedLanguages());
        problem.setCheckerType("EXACT");
        problem.setCheckerLanguage(null);
        problem.setCheckerSource(null);
        problem.setCheckerBinPath(null);
        problem.setPackageHash(null);
        problem = problemRepository.save(problem);

        problemService.saveTags(problem, pkg.tags());

        Map<String, Subtask> subtaskByKey = new HashMap<>();
        if (pkg.subtasks() != null) {
            for (ProblemPackage.SubtaskSpec spec : pkg.subtasks()) {
                Subtask st = subtaskRepository.save(Subtask.builder()
                        .problem(problem).name(spec.name()).score(spec.score()).orderIndex(spec.orderIndex())
                        .build());
                subtaskByKey.put(spec.key(), st);
            }
        }

        Path casesDir = Path.of(basePath, String.valueOf(problem.getId()), "cases");
        try {
            Files.createDirectories(casesDir);
            for (ProblemPackage.TestCaseSpec spec : pkg.testCases()) {
                TestCase tc = testCaseRepository.save(TestCase.builder()
                        .problem(problem)
                        .subtask(spec.subtaskKey() != null ? subtaskByKey.get(spec.subtaskKey()) : null)
                        .inputPath("").outputPath("")
                        .isSample(spec.sample())
                        .score(spec.score())
                        .orderIndex(spec.orderIndex())
                        .build());
                Path in = casesDir.resolve(tc.getId() + ".in");
                Path out = casesDir.resolve(tc.getId() + ".out");
                Files.writeString(in, spec.input(), StandardCharsets.UTF_8);
                Files.writeString(out, spec.output(), StandardCharsets.UTF_8);
                tc.setInputPath(in.toString());
                tc.setOutputPath(out.toString());
                testCaseRepository.save(tc);
            }

            if (pkg.checker() != null) {
                String bin = dockerRunner.compileChecker(pkg.checker().language(), pkg.checker().source(), problem.getId());
                problem.setCheckerType(pkg.checker().type() != null ? pkg.checker().type() : "CUSTOM");
                problem.setCheckerLanguage(pkg.checker().language());
                problem.setCheckerSource(pkg.checker().source());
                problem.setCheckerBinPath(bin);
            }
        } catch (IOException e) {
            // Rolls back the rows; the hash is never recorded so the upstream retries the sync.
            throw new JudgeException("PACKAGE_APPLY_FAILED",
                    "Failed to apply package: " + e.getMessage(),
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }

        problem.setPackageHash(hash);
        problem = problemRepository.save(problem);
        log.info("Grader key {} applied package {} → problem {} (hash {})",
                owner.getId(), externalKey, problem.getId(), hash.substring(0, 12));
        return ManagedProblemResponse.from(problem, true);
    }

    @Transactional
    public void delete(ApiKey owner, String externalKey) {
        Problem p = findOwned(owner, externalKey);
        problemService.deleteProblem(p.getId());
    }

    /** True when {@code problem} was pushed by {@code key} — the only problems a GRADER may judge. */
    public static boolean isOwnedBy(Problem problem, ApiKey key) {
        return problem.getExternalKey() != null
                && problem.getOwnerKeyId() != null
                && problem.getOwnerKeyId().equals(key.getId());
    }

    public static String hash(ProblemPackage pkg) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(CANONICAL.writeValueAsBytes(pkg));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash package", e);
        }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Problem findOwned(ApiKey owner, String externalKey) {
        validateExternalKey(externalKey);
        return problemRepository.findByOwnerKeyIdAndExternalKey(owner.getId(), externalKey)
                .orElseThrow(() -> JudgeException.notFound("Managed problem not found: " + externalKey));
    }

    private void clearGradingData(Problem problem) {
        for (TestCase tc : testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId())) {
            tryDelete(tc.getInputPath());
            tryDelete(tc.getOutputPath());
            testCaseRepository.delete(tc);
        }
        subtaskRepository.deleteAll(subtaskRepository.findByProblemIdOrderByOrderIndexAsc(problem.getId()));
        problemTagRepository.deleteByProblemId(problem.getId());
        testCaseRepository.flush();
    }

    static void validateExternalKey(String externalKey) {
        if (externalKey == null || !EXTERNAL_KEY.matcher(externalKey).matches()) {
            throw JudgeException.badRequest("externalKey must match [A-Za-z0-9_-]{1,64}");
        }
    }

    static void validateStructure(ProblemPackage pkg) {
        Set<String> keys = new HashSet<>();
        if (pkg.subtasks() != null) {
            for (ProblemPackage.SubtaskSpec st : pkg.subtasks()) {
                if (!keys.add(st.key())) throw JudgeException.badRequest("Duplicate subtask key: " + st.key());
            }
        }
        long chars = 0;
        for (ProblemPackage.TestCaseSpec tc : pkg.testCases()) {
            if (tc.subtaskKey() != null && !keys.contains(tc.subtaskKey())) {
                throw JudgeException.badRequest("Test case references unknown subtask: " + tc.subtaskKey());
            }
            chars += tc.input().length() + tc.output().length();
        }
        if (chars > MAX_PACKAGE_CHARS) {
            throw JudgeException.badRequest("Package too large (max " + MAX_PACKAGE_CHARS + " chars of test data)");
        }
    }

    /** Underscore keeps it disjoint from admin-authored slugs, which are validated as [a-z0-9-]+. */
    static String managedSlug(ApiKey owner, String externalKey) {
        String slug = "g" + owner.getId() + "_" + externalKey;
        return slug.length() > 100 ? slug.substring(0, 100) : slug;
    }

    private static String readFile(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JudgeException("TESTCASE_UNREADABLE", "Cannot read test file: " + path,
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static void tryDelete(String path) {
        if (path == null || path.isBlank()) return;
        try { Files.deleteIfExists(Path.of(path)); } catch (IOException ignored) {}
    }
}
