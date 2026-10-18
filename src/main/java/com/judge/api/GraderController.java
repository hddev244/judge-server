package com.judge.api;

import com.judge.api.dto.ManagedProblemResponse;
import com.judge.api.dto.ProblemPackage;
import com.judge.domain.ApiKey;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.ProblemPackageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Problems pushed by an upstream platform through a GRADER key. Every operation is scoped to the
 * calling key: a grader can neither see nor touch problems it did not create, nor locally
 * authored ones. ADMIN keys may use these endpoints too (under their own key id).
 */
@RestController
@RequestMapping("/api/v1/grader/problems")
public class GraderController {

    private final ProblemPackageService packageService;

    public GraderController(ProblemPackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping("/{externalKey}")
    public ResponseEntity<ManagedProblemResponse> get(@PathVariable String externalKey) {
        return ResponseEntity.ok(packageService.get(requireGrader(), externalKey));
    }

    @PutMapping("/{externalKey}")
    public ResponseEntity<ManagedProblemResponse> upsert(@PathVariable String externalKey,
                                                         @RequestBody @Valid ProblemPackage pkg) {
        return ResponseEntity.ok(packageService.upsert(requireGrader(), externalKey, pkg));
    }

    @DeleteMapping("/{externalKey}")
    public ResponseEntity<Void> delete(@PathVariable String externalKey) {
        packageService.delete(requireGrader(), externalKey);
        return ResponseEntity.noContent().build();
    }

    private static ApiKey requireGrader() {
        ApiKey key = ApiKeyContext.get();
        if (key == null || !(key.isGrader() || key.isAdmin())) {
            throw JudgeException.forbidden("Grader or admin key required");
        }
        return key;
    }
}
