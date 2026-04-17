package com.judge.service;

import com.judge.api.dto.SubmitRequest;
import com.judge.api.dto.SubmissionResponse;
import com.judge.domain.Problem;
import com.judge.domain.Submission;
import com.judge.exception.JudgeException;
import com.judge.repository.ProblemRepository;
import com.judge.repository.SubmissionRepository;
import com.judge.security.ApiKeyContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;

    public SubmissionService(SubmissionRepository submissionRepository,
                             ProblemRepository problemRepository) {
        this.submissionRepository = submissionRepository;
        this.problemRepository = problemRepository;
    }

    @Transactional
    public SubmissionResponse create(SubmitRequest req) {
        Problem problem = problemRepository.findById(req.getProblemId())
                .filter(Problem::isPublished)
                .orElseThrow(() -> JudgeException.notFound("Problem not found or not published"));

        String id = "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        Submission submission = Submission.builder()
                .id(id)
                .problem(problem)
                .language(req.getLanguage())
                .sourceCode(req.getSourceCode())
                .userRef(req.getUserRef())
                .callbackUrl(req.getCallbackUrl())
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
}
