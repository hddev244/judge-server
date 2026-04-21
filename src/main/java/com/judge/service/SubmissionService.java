package com.judge.service;

import com.judge.api.dto.SubmitRequest;
import com.judge.api.dto.SubmissionResponse;
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
                .filter(Problem::isPublished)
                .orElseThrow(() -> JudgeException.notFound("Problem not found or not published"));

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
        return SubmissionResponse.from(submission);
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> list(String problemSlug, String userRef, String status, int page, int size) {
        return submissionRepository.findByFilters(
                problemSlug, userRef, status,
                PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())
        ).map(SubmissionResponse::from);
    }
}
