package com.judge.service;

import com.judge.api.dto.ProblemRequest;
import com.judge.domain.Problem;
import com.judge.domain.ProblemTag;
import com.judge.repository.*;
import com.judge.judge.DockerRunner;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProblemServiceUpdateTagsTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemService service = new ProblemService(problemRepository, tagRepository,
            mock(TestCaseRepository.class), mock(SubtaskRepository.class), mock(TopicRepository.class),
            mock(CategoryRepository.class), mock(DockerRunner.class), "/tmp/judge-test");

    @Test
    void update_flushesTagDeletesBeforeReinserting() {
        // Regression: keeping a tag across an update returned 500 (duplicate problem_tags key)
        // because the queued deletes ran after the new inserts.
        Problem p = Problem.builder().id(7L).slug("a").title("A").timeLimitMs(1000).memoryLimitKb(262144).build();
        when(problemRepository.findById(7L)).thenReturn(Optional.of(p));
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        ProblemRequest req = new ProblemRequest();
        req.setSlug("a");
        req.setTitle("A2");
        req.setTags(List.of("math"));

        service.update(7L, req);

        InOrder order = inOrder(tagRepository);
        order.verify(tagRepository).deleteByProblemId(7L);
        order.verify(tagRepository).flush();
        order.verify(tagRepository).save(any(ProblemTag.class));
    }
}
