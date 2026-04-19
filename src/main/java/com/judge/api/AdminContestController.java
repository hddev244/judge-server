package com.judge.api;

import com.judge.api.dto.*;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.ContestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/contests")
public class AdminContestController {

    private final ContestService contestService;

    public AdminContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    @PostMapping
    public ResponseEntity<ContestResponse> create(@RequestBody @Valid ContestRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(contestService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContestResponse> update(@PathVariable Long id,
                                                   @RequestBody @Valid ContestRequest req) {
        requireAdmin();
        return ResponseEntity.ok(contestService.update(id, req));
    }

    @PostMapping("/{id}/problems")
    public ResponseEntity<ContestProblemResponse> addProblem(
            @PathVariable Long id,
            @RequestBody @Valid ContestProblemRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(contestService.addProblem(id, req));
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    public ResponseEntity<Void> removeProblem(@PathVariable Long id,
                                               @PathVariable Long problemId) {
        requireAdmin();
        contestService.removeProblem(id, problemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/scoreboard")
    public ResponseEntity<List<ScoreboardEntry>> scoreboard(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(contestService.getAdminScoreboard(id));
    }

    private void requireAdmin() {
        if (!ApiKeyContext.get().isAdmin()) {
            throw JudgeException.forbidden("Admin access required");
        }
    }
}
