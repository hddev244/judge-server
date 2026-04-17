package com.judge.api;

import com.judge.api.dto.ProblemRequest;
import com.judge.api.dto.ProblemResponse;
import com.judge.api.dto.TestCaseResponse;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.ProblemService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/problems")
public class AdminProblemController {

    private final ProblemService problemService;

    public AdminProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @PostMapping
    public ResponseEntity<ProblemResponse> create(@RequestBody @Valid ProblemRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(problemService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProblemResponse> update(@PathVariable Long id,
                                                   @RequestBody @Valid ProblemRequest req) {
        requireAdmin();
        return ResponseEntity.ok(problemService.update(id, req));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ProblemResponse> publish(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(problemService.publish(id));
    }

    @PostMapping(value = "/{id}/test-cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TestCaseResponse> addTestCase(
            @PathVariable Long id,
            @RequestPart("input") MultipartFile inputFile,
            @RequestPart("output") MultipartFile outputFile,
            @RequestParam(defaultValue = "false") boolean isSample,
            @RequestParam(defaultValue = "1") int score) throws IOException {
        requireAdmin();
        return ResponseEntity.status(201).body(
                problemService.addTestCase(id, inputFile, outputFile, isSample, score));
    }

    @GetMapping("/{id}/test-cases")
    public ResponseEntity<List<TestCaseResponse>> listTestCases(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(problemService.listTestCases(id));
    }

    @DeleteMapping("/{id}/test-cases/{caseId}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id,
                                                @PathVariable Long caseId) {
        requireAdmin();
        problemService.deleteTestCase(id, caseId);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin() {
        if (!ApiKeyContext.get().isAdmin()) {
            throw JudgeException.forbidden("Admin access required");
        }
    }
}
