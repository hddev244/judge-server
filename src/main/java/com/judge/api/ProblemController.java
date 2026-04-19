package com.judge.api;

import com.judge.api.dto.ProblemResponse;
import com.judge.api.dto.ProblemSearchResponse;
import com.judge.service.ProblemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<ProblemSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<String> tagList = (tags != null && !tags.isBlank())
                ? Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList()
                : null;
        size = Math.min(size, 100);
        return ResponseEntity.ok(problemService.search(q, tagList, difficulty, page, size));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProblemResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(problemService.getBySlug(slug));
    }
}
