package com.judge.service;

import com.judge.api.dto.TopicRequest;
import com.judge.api.dto.TopicResponse;
import com.judge.domain.Problem;
import com.judge.domain.Topic;
import com.judge.exception.JudgeException;
import com.judge.repository.ProblemRepository;
import com.judge.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TopicService {

    private final TopicRepository topicRepository;
    private final ProblemRepository problemRepository;

    public TopicService(TopicRepository topicRepository, ProblemRepository problemRepository) {
        this.topicRepository = topicRepository;
        this.problemRepository = problemRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listAll() {
        return topicRepository.findAllByOrderByNameAsc().stream()
                .map(t -> TopicResponse.from(t, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicResponse getById(Long id) {
        return TopicResponse.from(getOrThrow(id), true);
    }

    @Transactional(readOnly = true)
    public TopicResponse getBySlug(String slug) {
        Topic topic = topicRepository.findBySlug(slug)
                .orElseThrow(() -> JudgeException.notFound("Topic not found: " + slug));
        return TopicResponse.from(topic, true);
    }

    @Transactional
    public TopicResponse create(TopicRequest req) {
        if (topicRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Topic slug already exists: " + req.getSlug());
        if (topicRepository.existsByName(req.getName()))
            throw JudgeException.badRequest("Topic name already exists: " + req.getName());

        Topic topic = Topic.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .build();
        return TopicResponse.from(topicRepository.save(topic), false);
    }

    @Transactional
    public TopicResponse update(Long id, TopicRequest req) {
        Topic topic = getOrThrow(id);
        if (!topic.getSlug().equals(req.getSlug()) && topicRepository.existsBySlug(req.getSlug()))
            throw JudgeException.badRequest("Topic slug already exists: " + req.getSlug());
        if (!topic.getName().equals(req.getName()) && topicRepository.existsByName(req.getName()))
            throw JudgeException.badRequest("Topic name already exists: " + req.getName());

        topic.setName(req.getName());
        topic.setSlug(req.getSlug());
        topic.setDescription(req.getDescription());
        return TopicResponse.from(topicRepository.save(topic), true);
    }

    @Transactional
    public void delete(Long id) {
        topicRepository.delete(getOrThrow(id));
    }

    @Transactional
    public TopicResponse addProblems(Long topicId, List<Long> problemIds) {
        Topic topic = getOrThrow(topicId);
        for (Long pid : problemIds) {
            Problem p = problemRepository.findById(pid)
                    .orElseThrow(() -> JudgeException.notFound("Problem not found: " + pid));
            topic.getProblems().add(p);
            p.getTopics().add(topic);
        }
        topicRepository.save(topic);
        return TopicResponse.from(topic, true);
    }

    @Transactional
    public TopicResponse removeProblem(Long topicId, Long problemId) {
        Topic topic = getOrThrow(topicId);
        Problem p = problemRepository.findById(problemId)
                .orElseThrow(() -> JudgeException.notFound("Problem not found: " + problemId));
        topic.getProblems().remove(p);
        p.getTopics().remove(topic);
        topicRepository.save(topic);
        return TopicResponse.from(topic, true);
    }

    private Topic getOrThrow(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> JudgeException.notFound("Topic not found: " + id));
    }
}
