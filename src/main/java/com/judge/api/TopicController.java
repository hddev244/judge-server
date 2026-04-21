package com.judge.api;

import com.judge.api.dto.TopicResponse;
import com.judge.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<TopicResponse>> list() {
        return ResponseEntity.ok(topicService.listAll());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<TopicResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(topicService.getBySlug(slug));
    }
}
