package com.judge.api;

import com.judge.api.dto.ProblemResponse;
import com.judge.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<List<ProblemResponse>> list() {
        return ResponseEntity.ok(problemService.listPublished());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProblemResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getBySlug(slug));
    }
}
