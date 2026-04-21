package com.judge.api;

import com.judge.api.dto.CategoryRequest;
import com.judge.api.dto.CategoryResponse;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        requireAdmin();
        return ResponseEntity.ok(categoryService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> get(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(categoryService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@RequestBody @Valid CategoryRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(categoryService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@PathVariable Long id,
                                                    @RequestBody @Valid CategoryRequest req) {
        requireAdmin();
        return ResponseEntity.ok(categoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        requireAdmin();
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/problems")
    public ResponseEntity<CategoryResponse> addProblems(@PathVariable Long id,
                                                         @RequestBody Map<String, List<Long>> body) {
        requireAdmin();
        List<Long> problemIds = body.get("problemIds");
        if (problemIds == null || problemIds.isEmpty())
            throw JudgeException.badRequest("problemIds is required");
        return ResponseEntity.ok(categoryService.addProblems(id, problemIds));
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    public ResponseEntity<CategoryResponse> removeProblem(@PathVariable Long id,
                                                           @PathVariable Long problemId) {
        requireAdmin();
        return ResponseEntity.ok(categoryService.removeProblem(id, problemId));
    }

    private void requireAdmin() {
        if (!ApiKeyContext.get().isAdmin())
            throw JudgeException.forbidden("Admin access required");
    }
}
