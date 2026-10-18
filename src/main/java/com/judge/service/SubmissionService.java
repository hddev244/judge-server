package com.judge.service;

import com.judge.api.dto.SubmitRequest;
import com.judge.api.dto.SubmissionResponse;
import com.judge.domain.ApiKey;
import com.judge.domain.Contest;
import com.judge.domain.Problem;
import com.judge.domain.Submission;
import com.judge.exception.JudgeException;
import com.judge.repository.ProblemRepository;
import com.judge.repository.SubmissionRepository;
import com.judge.security.ApiKeyContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final ContestService contestService;

    public SubmissionService(SubmissionRepository submissionRepository,
                             ProblemRepository problemRepository,
                             ContestService contestService) {
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
        this.contestService = contestService;
    }

    @Transactional
    public SubmissionResponse create(SubmitRequest req) {
        Problem problem = problemRepository.findById(req.getProblemId())
                .orElseThrow(() -> JudgeException.notFound("Problem not found"));

        ApiKey caller = ApiKeyContext.get();
        if (caller != null && caller.isGrader()) {
            // Graders judge only the (private) problems they pushed, and never join contests.
            if (!ProblemPackageService.isOwnedBy(problem, caller)) {
                throw JudgeException.notFound("Problem not found or not available");
            }
            if (req.getContestId() != null) {
                throw JudgeException.forbidden("Grader keys cannot submit to contests");
            }
        } else {
            String problemStatus = problem.getStatus() != null ? problem.getStatus() : "PRIVATE";
            if ("PRIVATE".equals(problemStatus)) {
                throw JudgeException.notFound("Problem not found or not available");
            }
        }

        if (problem.getAllowedLanguages() != null && !problem.getAllowedLanguages().isBlank()) {
            List<String> allowed = java.util.Arrays.asList(problem.getAllowedLanguages().split(","));
            if (!allowed.contains(req.getLanguage())) {
                throw JudgeException.badRequest(
                        "Language '" + req.getLanguage() + "' is not allowed for this problem. Allowed: " + allowed);
            }
        }

        Long contestId = null;
        if (req.getContestId() != null) {
            Contest contest = contestService.validateContestSubmission(
                    req.getContestId(), req.getProblemId(), req.getUserRef());
            contestId = contest.getId();
        }

        String id = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        Submission submission = Submission.builder()
                .id(id)
                .problem(problem)
                .language(req.getLanguage())
                .sourceCode(req.getSourceCode())
                .userRef(req.getUserRef())
                .callbackUrl(req.getCallbackUrl())
                .contestId(contestId)
                .status("PENDING")
                .score(0)
                .client(ApiKeyContext.get())
                .build();

        return SubmissionResponse.from(submissionRepository.save(submission));
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(String id) {
        Submission submission = submissionRepository.findByIdWithResults(id)
                .orElseThrow(() -> JudgeException.notFound("Submission not found: " + id));
        Long scope = graderScope();
        if (scope != null && (submission.getClient() == null || !scope.equals(submission.getClient().getId()))) {
            throw JudgeException.notFound("Submission not found: " + id);
        }
        return SubmissionResponse.from(submission);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> list(String problemSlug, String userRef, String status, int page, int size) {
        return submissionRepository.findByFilters(
                problemSlug, userRef, status, graderScope(),
                PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())
        ).map(SubmissionResponse::from);
    }

    /** Grader keys only ever see submissions they created; null = unscoped. */
    private static Long graderScope() {
        ApiKey caller = ApiKeyContext.get();
        return caller != null && caller.isGrader() ? caller.getId() : null;
    }
}
