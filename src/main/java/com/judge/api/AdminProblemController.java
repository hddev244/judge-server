package com.judge.api;

import com.judge.api.dto.*;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.ProblemImportService;
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
    private final ProblemImportService importService;

    public AdminProblemController(ProblemService problemService,
                                   ProblemImportService importService) {
        this.problemService = problemService;
        this.importService = importService;
    }

    // ─── Problem CRUD ───────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ProblemResponse> getById(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(problemService.getById(id));
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

    // ─── Import ─────────────────────────────────────────────────────────────

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProblemResponse> importZip(
            @RequestPart("file") MultipartFile file) throws IOException {
        requireAdmin();
        return ResponseEntity.status(201).body(importService.importZip(file.getInputStream()));
    }

    // ─── Checker ────────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/checker", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProblemResponse> uploadChecker(
            @PathVariable Long id,
            @RequestParam String language,
            @RequestPart("source") MultipartFile source) throws IOException {
        requireAdmin();
        return ResponseEntity.ok(
                problemService.uploadChecker(id, language, new String(source.getBytes())));
    }

    @DeleteMapping("/{id}/checker")
    public ResponseEntity<ProblemResponse> removeChecker(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(problemService.removeChecker(id));
    }

    // ─── Subtasks ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/subtasks")
    public ResponseEntity<SubtaskResponse> addSubtask(
            @PathVariable Long id,
            @RequestBody @Valid SubtaskRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(problemService.addSubtask(id, req));
    }

    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<SubtaskResponse>> listSubtasks(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(problemService.listSubtasks(id));
    }

    @DeleteMapping("/{id}/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(@PathVariable Long id,
                                               @PathVariable Long subtaskId) {
        requireAdmin();
        problemService.deleteSubtask(id, subtaskId);
        return ResponseEntity.noContent().build();
    }

    // ─── Test Cases ──────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/test-cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TestCaseResponse> addTestCase(
            @PathVariable Long id,
            @RequestPart("input") MultipartFile inputFile,
            @RequestPart("output") MultipartFile outputFile,
            @RequestParam(defaultValue = "false") boolean isSample,
            @RequestParam(defaultValue = "1") int score,
            @RequestParam(required = false) Long subtaskId) throws IOException {
        requireAdmin();
        return ResponseEntity.status(201).body(
                problemService.addTestCase(id, inputFile, outputFile, isSample, score, subtaskId));
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
