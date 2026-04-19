package com.judge.api;

import com.judge.api.dto.*;
import com.judge.service.ContestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/contests")
public class ContestController {

    private final ContestService contestService;

    public ContestController(ContestService contestService) {
        this.contestService = contestService;
    }

    @GetMapping
    public ResponseEntity<List<ContestResponse>> listActive() {
        return ResponseEntity.ok(contestService.listActive());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ContestResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(contestService.getBySlug(slug));
    }

    @PostMapping("/{slug}/register")
    public ResponseEntity<Void> register(@PathVariable String slug,
                                          @RequestParam String userRef) {
        contestService.register(slug, userRef);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{slug}/scoreboard")
    public ResponseEntity<List<ScoreboardEntry>> scoreboard(@PathVariable String slug) {
        return ResponseEntity.ok(contestService.getPublicScoreboard(slug));
    }
}
