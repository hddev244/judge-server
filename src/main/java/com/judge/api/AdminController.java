package com.judge.api;

import com.judge.api.dto.*;
import com.judge.domain.ApiKey;
import com.judge.exception.JudgeException;
import com.judge.repository.ApiKeyRepository;
import com.judge.repository.ProblemRepository;
import com.judge.repository.SubmissionRepository;
import com.judge.security.ApiKeyContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final ApiKeyRepository apiKeyRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;

    public AdminController(ApiKeyRepository apiKeyRepository,
                           ProblemRepository problemRepository,
                           SubmissionRepository submissionRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
    }

    // ── API Keys ──────────────────────────────────────────────

    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiKeyResponse>> listKeys() {
        requireAdmin();
        return ResponseEntity.ok(
                apiKeyRepository.findAll(Sort.by("id").descending())
                        .stream().map(ApiKeyResponse::from).toList()
        );
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiKeyResponse> createKey(@RequestBody @Valid ApiKeyRequest req) {
        requireAdmin();
        String raw = "sk_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey key = ApiKey.builder()
                .key(raw)
                .clientName(req.getClientName())
                .isActive(true)
                .isAdmin(req.isAdmin())
                .rateLimitPerHour(req.getRateLimitPerHour())
                .build();
        return ResponseEntity.status(201).body(ApiKeyResponse.from(apiKeyRepository.save(key)));
    }

    @PatchMapping("/api-keys/{id}/deactivate")
    public ResponseEntity<ApiKeyResponse> deactivateKey(@PathVariable Long id) {
        requireAdmin();
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("API key not found"));
        key.setActive(false);
        return ResponseEntity.ok(ApiKeyResponse.from(apiKeyRepository.save(key)));
    }

    @PatchMapping("/api-keys/{id}/activate")
    public ResponseEntity<ApiKeyResponse> activateKey(@PathVariable Long id) {
        requireAdmin();
        ApiKey key = apiKeyRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("API key not found"));
        key.setActive(true);
        return ResponseEntity.ok(ApiKeyResponse.from(apiKeyRepository.save(key)));
    }

    // ── Problems ──────────────────────────────────────────────

    @GetMapping("/problems")
    public ResponseEntity<Page<ProblemResponse>> listProblems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        return ResponseEntity.ok(
                problemRepository.findAll(PageRequest.of(page, size, Sort.by("id").descending()))
                        .map(ProblemResponse::from)
        );
    }

    // ── Submissions ───────────────────────────────────────────

    @GetMapping("/submissions")
    public ResponseEntity<Page<SubmissionResponse>> listSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        return ResponseEntity.ok(
                submissionRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(SubmissionResponse::from)
        );
    }

    // ── Stats ─────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        requireAdmin();
        return ResponseEntity.ok(java.util.Map.of(
                "totalProblems",    problemRepository.count(),
                "publishedProblems", problemRepository.findAll().stream().filter(p -> p.isPublished()).count(),
                "totalSubmissions", submissionRepository.count(),
                "totalApiKeys",     apiKeyRepository.count(),
                "activeApiKeys",    apiKeyRepository.findAll().stream().filter(ApiKey::isActive).count()
        ));
    }

    private void requireAdmin() {
        if (!ApiKeyContext.get().isAdmin()) throw JudgeException.forbidden("Admin access required");
    }
}
