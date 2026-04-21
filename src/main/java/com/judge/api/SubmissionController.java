package com.judge.api;

import com.judge.api.dto.SubmitRequest;
import com.judge.api.dto.SubmissionResponse;
import com.judge.api.dto.TestRunRequest;
import com.judge.judge.JudgeService;
import com.judge.queue.JudgeQueueService;
import com.judge.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;
    private final JudgeQueueService judgeQueueService;
    private final JudgeService judgeService;

    public SubmissionController(SubmissionService submissionService,
                                JudgeQueueService judgeQueueService,
                                JudgeService judgeService) {
        this.submissionService = submissionService;
        this.judgeQueueService = judgeQueueService;
        this.judgeService = judgeService;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(@RequestBody @Valid SubmitRequest req) {
        SubmissionResponse resp = submissionService.create(req);
        judgeQueueService.enqueue(resp.getSubmissionId());
        return ResponseEntity.status(202).body(resp);
    }

    @PostMapping("/test")
    public ResponseEntity<SubmissionResponse> test(@RequestBody @Valid TestRunRequest req) {
        return ResponseEntity.ok(judgeService.runTest(req));
    }

    @GetMapping
    public ResponseEntity<Page<SubmissionResponse>> list(
            @RequestParam(required = false) String problemSlug,
            @RequestParam(required = false) String userRef,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(submissionService.list(problemSlug, userRef, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(submissionService.getById(id));
    }
}
