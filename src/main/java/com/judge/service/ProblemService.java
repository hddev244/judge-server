package com.judge.service;

import com.judge.api.dto.ProblemRequest;
import com.judge.api.dto.ProblemResponse;
import com.judge.api.dto.TestCaseResponse;
import com.judge.domain.Problem;
import com.judge.domain.TestCase;
import com.judge.exception.JudgeException;
import com.judge.repository.ProblemRepository;
import com.judge.repository.TestCaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final String basePath;

    public ProblemService(ProblemRepository problemRepository,
                          TestCaseRepository testCaseRepository,
                          @Value("${judge.testcase.base-path}") String basePath) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
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
                .timeLimitMs(req.getTimeLimitMs())
                .memoryLimitKb(req.getMemoryLimitKb())
                .isPublished(false)
                .build();
        return ProblemResponse.from(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse update(Long id, ProblemRequest req) {
        Problem problem = getOrThrow(id);
        problem.setTitle(req.getTitle());
        problem.setDescription(req.getDescription());
        problem.setTimeLimitMs(req.getTimeLimitMs());
        problem.setMemoryLimitKb(req.getMemoryLimitKb());
        return ProblemResponse.from(problemRepository.save(problem));
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> listPublished() {
        return problemRepository.findByIsPublishedTrueOrderByIdAsc()
                .stream().map(ProblemResponse::from).toList();
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

    @Transactional
    public TestCaseResponse addTestCase(Long problemId, MultipartFile inputFile,
                                        MultipartFile outputFile, boolean isSample, int score) throws IOException {
        Problem problem = getOrThrow(problemId);

        int orderIndex = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(problemId).size();

        TestCase tc = TestCase.builder()
                .problem(problem)
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

    private Problem getOrThrow(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + id));
    }

    private void tryDelete(String path) {
        try { Files.deleteIfExists(Path.of(path)); } catch (IOException ignored) {}
    }
}
