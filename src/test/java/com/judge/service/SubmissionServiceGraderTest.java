package com.judge.service;

import com.judge.api.dto.SubmitRequest;
import com.judge.domain.ApiKey;
import com.judge.domain.ApiKeyRole;
import com.judge.domain.Problem;
import com.judge.domain.Submission;
import com.judge.exception.JudgeException;
import com.judge.repository.ProblemRepository;
import com.judge.repository.SubmissionRepository;
import com.judge.security.ApiKeyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmissionServiceGraderTest {

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final ProblemRepository problems = mock(ProblemRepository.class);
    private final ContestService contests = mock(ContestService.class);
    private final SubmissionService service = new SubmissionService(submissions, problems, contests);

    private final ApiKey grader = ApiKey.builder().id(10L).role(ApiKeyRole.GRADER).build();
    private final ApiKey client = ApiKey.builder().id(20L).role(ApiKeyRole.CLIENT).build();

    @AfterEach
    void clear() {
        ApiKeyContext.clear();
    }

    private SubmitRequest request(long problemId) {
        SubmitRequest req = new SubmitRequest();
        req.setProblemId(problemId);
        req.setLanguage("cpp");
        req.setSourceCode("int main(){}");
        req.setUserRef("u1");
        return req;
    }

    @Test
    void graderCanSubmitToItsOwnPrivateProblem() {
        ApiKeyContext.set(grader);
        Problem own = Problem.builder().id(1L).status("PRIVATE").externalKey("p1").ownerKeyId(10L).build();
        when(problems.findById(1L)).thenReturn(Optional.of(own));
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(service.create(request(1)).getSubmissionId());
    }

    @Test
    void graderCannotSubmitToPublicOrForeignProblems() {
        ApiKeyContext.set(grader);
        Problem publicLocal = Problem.builder().id(2L).status("PUBLIC").build();
        Problem foreign = Problem.builder().id(3L).status("PRIVATE").externalKey("p3").ownerKeyId(99L).build();
        when(problems.findById(2L)).thenReturn(Optional.of(publicLocal));
        when(problems.findById(3L)).thenReturn(Optional.of(foreign));

        assertThrows(JudgeException.class, () -> service.create(request(2)));
        assertThrows(JudgeException.class, () -> service.create(request(3)));
    }

    @Test
    void graderCannotJoinContests() {
        ApiKeyContext.set(grader);
        Problem own = Problem.builder().id(1L).status("PRIVATE").externalKey("p1").ownerKeyId(10L).build();
        when(problems.findById(1L)).thenReturn(Optional.of(own));
        SubmitRequest req = request(1);
        req.setContestId(5L);

        assertThrows(JudgeException.class, () -> service.create(req));
        verifyNoInteractions(contests);
    }

    @Test
    void clientKeysStillCannotSubmitToPrivateProblems() {
        ApiKeyContext.set(client);
        Problem managed = Problem.builder().id(1L).status("PRIVATE").externalKey("p1").ownerKeyId(10L).build();
        when(problems.findById(1L)).thenReturn(Optional.of(managed));

        assertThrows(JudgeException.class, () -> service.create(request(1)));
    }

    @Test
    void graderOnlySeesItsOwnSubmissions() {
        ApiKeyContext.set(grader);
        Submission foreign = Submission.builder().id("sub_x").client(client).status("AC").build();
        when(submissions.findByIdWithResults("sub_x")).thenReturn(Optional.of(foreign));

        assertThrows(JudgeException.class, () -> service.getById("sub_x"));
    }

    @Test
    void graderListingIsScopedToItsKey() {
        ApiKeyContext.set(grader);
        when(submissions.findByFilters(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(null, null, null, 0, 20);

        verify(submissions).findByFilters(isNull(), isNull(), isNull(), eq(10L), any());
    }

    @Test
    void clientListingIsUnscoped() {
        ApiKeyContext.set(client);
        when(submissions.findByFilters(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.list(null, null, null, 0, 20);

        verify(submissions).findByFilters(isNull(), isNull(), isNull(), isNull(), any());
    }
}
