package com.judge.webhook;

import com.judge.domain.WebhookRetry;
import com.judge.repository.WebhookRetryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookRetryJobTest {

    private final WebhookRetryRepository repo = mock(WebhookRetryRepository.class);
    private final WebhookSender sender = mock(WebhookSender.class);
    private final WebhookRetryJob job = new WebhookRetryJob(repo, sender);

    private WebhookRetry row(int attempts) {
        return WebhookRetry.builder().id(1L).submissionId("sub_x").callbackUrl("http://up/cb")
                .payload("{\"a\":1}").signature("old-signature").attemptCount(attempts)
                .nextRetryAt(LocalDateTime.now().minusSeconds(1)).failedPermanently(false).build();
    }

    @Test
    void failedRetry_backsOffOnTheSameRow_neverQueuesANewOne() {
        // Regression: the job called send(), which swallows the error and inserts a fresh row at
        // attempt 1, so an unreachable callback was retried every minute forever.
        WebhookRetry r = row(1);
        when(repo.findPendingRetries(any())).thenReturn(List.of(r));
        when(sender.signatureFor(any(), any())).thenReturn("sig");
        doThrow(new ResourceAccessException("refused")).when(sender).deliver(any(), any(), any(), any());

        job.retryFailedWebhooks();

        assertEquals(2, r.getAttemptCount());
        assertFalse(r.isFailedPermanently());
        verify(sender, never()).send(any(), any(), any(), any());
        verify(repo).save(r);
        verify(repo, never()).delete(any());
    }

    @Test
    void fourthFailure_marksPermanent() {
        WebhookRetry r = row(3);
        when(repo.findPendingRetries(any())).thenReturn(List.of(r));
        when(sender.signatureFor(any(), any())).thenReturn("sig");
        doThrow(new ResourceAccessException("refused")).when(sender).deliver(any(), any(), any(), any());

        job.retryFailedWebhooks();

        assertTrue(r.isFailedPermanently());
    }

    @Test
    void delivered_isDeleted_andResignedWithCurrentKey() {
        WebhookRetry r = row(1);
        when(repo.findPendingRetries(any())).thenReturn(List.of(r));
        when(sender.signatureFor("{\"a\":1}", "http://up/cb")).thenReturn("fresh-signature");

        job.retryFailedWebhooks();

        verify(sender).deliver("http://up/cb", "{\"a\":1}", "fresh-signature", "sub_x");
        verify(repo).delete(r);
    }
}
