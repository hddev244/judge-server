package com.judge.service;

import com.judge.api.dto.*;
import com.judge.domain.*;
import com.judge.exception.JudgeException;
import com.judge.judge.DockerRunner;
import com.judge.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TestCaseRepository testCaseRepository;
    private final SubtaskRepository subtaskRepository;
    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final DockerRunner dockerRunner;
    private final String basePath;

    public ProblemService(ProblemRepository problemRepository,
                          ProblemTagRepository problemTagRepository,
                          TestCaseRepository testCaseRepository,
                          SubtaskRepository subtaskRepository,
                          TopicRepository topicRepository,
                          CategoryRepository categoryRepository,
                          DockerRunner dockerRunner,
                          @Value("${judge.testcase.base-path}") String basePath) {
        this.problemRepository = problemRepository;
        this.problemTagRepository = problemTagRepository;
        this.testCaseRepository = testCaseRepository;
        this.subtaskRepository = subtaskRepository;
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
        this.dockerRunner = dockerRunner;
        this.basePath = basePath;
    }

    @Transactional
    public ProblemResponse create(ProblemRequest req) {
        if (problemRepository.findBySlug(req.getSlug()).isPresent()) {
            throw JudgeException.badRequest("Slug already exists: " + req.getSlug());
        }
        Problem problem = Problem.builder()
                .slug(req.getSlug())
                .title(req.getTitle())
                .description(req.getDescription())
                .descriptionFormat(req.getDescriptionFormat() != null ? req.getDescriptionFormat() : "MARKDOWN")
                .timeLimitMs(req.getTimeLimitMs())
                .memoryLimitKb(req.getMemoryLimitKb())
                .difficulty(req.getDifficulty())
                .isPublished(false)
                .build();
        problem = problemRepository.save(problem);
        List<String> tags = saveTags(problem, req.getTags());
        syncTopics(problem, req.getTopicIds());
        syncCategories(problem, req.getCategoryIds());
        return ProblemResponse.from(problem);
    }

    @Transactional
    public ProblemResponse update(Long id, ProblemRequest req) {
        Problem problem = getOrThrow(id);
        problem.setTitle(req.getTitle());
        problem.setDescription(req.getDescription());
        problem.setDescriptionFormat(req.getDescriptionFormat() != null ? req.getDescriptionFormat() : "MARKDOWN");
        problem.setTimeLimitMs(req.getTimeLimitMs());
        problem.setMemoryLimitKb(req.getMemoryLimitKb());
        problem.setDifficulty(req.getDifficulty());
        problem = problemRepository.save(problem);
        problemTagRepository.deleteByProblemId(id);
        List<String> tags = saveTags(problem, req.getTags());
        syncTopics(problem, req.getTopicIds());
        syncCategories(problem, req.getCategoryIds());
        return ProblemResponse.from(problem);
    }

    @Transactional(readOnly = true)
    public ProblemSearchResponse search(String q, List<String> tags, String difficulty,
                                         String topicSlug, String categorySlug,
                                         int page, int size) {
        Specification<Problem> spec = ProblemSpecification.isPublished();
        if (q != null && !q.isBlank())
            spec = spec.and(ProblemSpecification.titleContains(q.trim()));
        if (difficulty != null && !difficulty.isBlank())
            spec = spec.and(ProblemSpecification.hasDifficulty(difficulty));
        if (tags != null && !tags.isEmpty())
            spec = spec.and(ProblemSpecification.hasTags(tags));
        if (topicSlug != null && !topicSlug.isBlank())
            spec = spec.and(ProblemSpecification.hasTopic(topicSlug.trim()));
        if (categorySlug != null && !categorySlug.isBlank())
            spec = spec.and(ProblemSpecification.hasCategory(categorySlug.trim()));

        Page<Problem> result = problemRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("id").ascending()));

        List<ProblemResponse> content = result.getContent().stream()
                .map(ProblemResponse::from)
                .toList();

        return ProblemSearchResponse.builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> listPublished() {
        return problemRepository.findByIsPublishedTrueOrderByIdAsc()
                .stream().map(ProblemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse getById(Long id) {
        return ProblemResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public ProblemResponse getBySlug(String slug) {
        return problemRepository.findBySlugAndIsPublishedTrue(slug)
                .map(ProblemResponse::from)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + slug));
    }

    @Transactional
    public ProblemResponse publish(Long id) {
        Problem problem = getOrThrow(id);
        long testCaseCount = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(id).size();
        if (testCaseCount == 0) {
            throw JudgeException.badRequest("Cannot publish problem with no test cases");
        }
        problem.setPublished(true);
        return ProblemResponse.from(problemRepository.save(problem));
    }

    // ─── Subtask CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public SubtaskResponse addSubtask(Long problemId, SubtaskRequest req) {
        Problem problem = getOrThrow(problemId);
        int orderIndex = req.getOrderIndex() > 0 ? req.getOrderIndex()
                : subtaskRepository.findByProblemIdOrderByOrderIndexAsc(problemId).size();
        Subtask subtask = Subtask.builder()
                .problem(problem)
                .name(req.getName())
                .score(req.getScore())
                .orderIndex(orderIndex)
                .build();
        return SubtaskResponse.from(subtaskRepository.save(subtask));
    }

    @Transactional(readOnly = true)
    public List<SubtaskResponse> listSubtasks(Long problemId) {
        getOrThrow(problemId);
        return subtaskRepository.findByProblemIdOrderByOrderIndexAsc(problemId)
                .stream().map(SubtaskResponse::from).toList();
    }

    @Transactional
    public void deleteSubtask(Long problemId, Long subtaskId) {
        getOrThrow(problemId);
        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> JudgeException.notFound("Subtask not found: " + subtaskId));
        if (!subtask.getProblem().getId().equals(problemId)) {
            throw JudgeException.forbidden("Subtask does not belong to this problem");
        }
        subtaskRepository.delete(subtask);
    }

    // ─── Test Case CRUD ────────────────────────────────────────────────────────

    @Transactional
    public TestCaseResponse addTestCase(Long problemId, MultipartFile inputFile,
                                        MultipartFile outputFile, boolean isSample,
                                        int score, Long subtaskId) throws IOException {
        Problem problem = getOrThrow(problemId);

        Subtask subtask = null;
        if (subtaskId != null) {
            subtask = subtaskRepository.findById(subtaskId)
                    .orElseThrow(() -> JudgeException.notFound("Subtask not found: " + subtaskId));
            if (!subtask.getProblem().getId().equals(problemId)) {
                throw JudgeException.forbidden("Subtask does not belong to this problem");
            }
        }

        int orderIndex = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId).size();

        TestCase tc = TestCase.builder()
                .problem(problem)
                .subtask(subtask)
                .inputPath("")
                .outputPath("")
                .isSample(isSample)
                .score(score)
                .orderIndex(orderIndex)
                .build();
        tc = testCaseRepository.save(tc);

        Path dir = Path.of(basePath, String.valueOf(problemId), "cases");
        Files.createDirectories(dir);

        Path inPath = dir.resolve(tc.getId() + ".in");
        Path outPath = dir.resolve(tc.getId() + ".out");
        Files.write(inPath, inputFile.getBytes());
        Files.write(outPath, outputFile.getBytes());

        tc.setInputPath(inPath.toString());
        tc.setOutputPath(outPath.toString());
        testCaseRepository.save(tc);

        return TestCaseResponse.from(tc);
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> listTestCases(Long problemId) {
        getOrThrow(problemId);
        return testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId)
                .stream().map(TestCaseResponse::from).toList();
    }

    @Transactional
    public void deleteTestCase(Long problemId, Long caseId) {
        getOrThrow(problemId);
        TestCase tc = testCaseRepository.findById(caseId)
                .orElseThrow(() -> JudgeException.notFound("Test case not found: " + caseId));
        if (!tc.getProblem().getId().equals(problemId)) {
            throw JudgeException.forbidden("Test case does not belong to this problem");
        }
        tryDelete(tc.getInputPath());
        tryDelete(tc.getOutputPath());
        testCaseRepository.delete(tc);
    }

    // ─── Checker ───────────────────────────────────────────────────────────────

    @Transactional
    public ProblemResponse uploadChecker(Long problemId, String language, String sourceCode) throws IOException {
        Problem problem = getOrThrow(problemId);
        String binPath = dockerRunner.compileChecker(language, sourceCode, problemId);
        problem.setCheckerType("CUSTOM");
        problem.setCheckerLanguage(language);
        problem.setCheckerSource(sourceCode);
        problem.setCheckerBinPath(binPath);
        return ProblemResponse.from(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse removeChecker(Long problemId) {
        Problem problem = getOrThrow(problemId);
        problem.setCheckerType("EXACT");
        problem.setCheckerLanguage(null);
        problem.setCheckerSource(null);
        problem.setCheckerBinPath(null);
        return ProblemResponse.from(problemRepository.save(problem));
    }

    private Problem getOrThrow(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + id));
    }

    private void tryDelete(String path) {
        try { Files.deleteIfExists(Path.of(path)); } catch (IOException ignored) {}
    }

    private void syncTopics(Problem problem, List<Long> topicIds) {
        problem.getTopics().forEach(t -> t.getProblems().remove(problem));
        problem.getTopics().clear();
        if (topicIds == null || topicIds.isEmpty()) return;
        topicIds.stream().distinct().forEach(tid ->
                topicRepository.findById(tid).ifPresent(t -> {
                    problem.getTopics().add(t);
                    t.getProblems().add(problem);
                }));
    }

    private void syncCategories(Problem problem, List<Long> categoryIds) {
        problem.getCategories().forEach(c -> c.getProblems().remove(problem));
        problem.getCategories().clear();
        if (categoryIds == null || categoryIds.isEmpty()) return;
        categoryIds.stream().distinct().forEach(cid ->
                categoryRepository.findById(cid).ifPresent(c -> {
                    problem.getCategories().add(c);
                    c.getProblems().add(problem);
                }));
    }

    /** Persists the tag list and returns the normalized tag strings. */
    List<String> saveTags(Problem problem, List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) return List.of();
        return rawTags.stream()
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .distinct()
                .map(tag -> {
                    problemTagRepository.save(ProblemTag.builder()
                            .problem(problem).tag(tag).build());
                    return tag;
                })
                .toList();
    }
}
