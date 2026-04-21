package com.judge.api;

import com.judge.api.dto.TopicRequest;
import com.judge.api.dto.TopicResponse;
import com.judge.exception.JudgeException;
import com.judge.security.ApiKeyContext;
import com.judge.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/topics")
public class AdminTopicController {

    private final TopicService topicService;

    public AdminTopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<TopicResponse>> list() {
        requireAdmin();
        return ResponseEntity.ok(topicService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopicResponse> get(@PathVariable Long id) {
        requireAdmin();
        return ResponseEntity.ok(topicService.getById(id));
    }

    @PostMapping
    public ResponseEntity<TopicResponse> create(@RequestBody @Valid TopicRequest req) {
        requireAdmin();
        return ResponseEntity.status(201).body(topicService.create(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TopicResponse> update(@PathVariable Long id,
                                                 @RequestBody @Valid TopicRequest req) {
        requireAdmin();
        return ResponseEntity.ok(topicService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        requireAdmin();
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/problems")
    public ResponseEntity<TopicResponse> addProblems(@PathVariable Long id,
                                                      @RequestBody Map<String, List<Long>> body) {
        requireAdmin();
        List<Long> problemIds = body.get("problemIds");
        if (problemIds == null || problemIds.isEmpty())
            throw JudgeException.badRequest("problemIds is required");
        return ResponseEntity.ok(topicService.addProblems(id, problemIds));
    }

    @DeleteMapping("/{id}/problems/{problemId}")
    public ResponseEntity<TopicResponse> removeProblem(@PathVariable Long id,
                                                        @PathVariable Long problemId) {
        requireAdmin();
        return ResponseEntity.ok(topicService.removeProblem(id, problemId));
    }

    private void requireAdmin() {
        if (!ApiKeyContext.get().isAdmin())
            throw JudgeException.forbidden("Admin access required");
    }
}
