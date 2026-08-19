package com.highpay.payment.infrastructure.persistence.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OutboxRetentionJobTest {

    @Test
    void shouldDeletePublishedAndFailedEventsOlderThanConfiguredTtl() {
        JpaOutboxEventRepository repository = mock(JpaOutboxEventRepository.class);
        when(repository.deleteByStatusAndPublishedAtBefore(eq(OutboxEventStatus.PUBLISHED), any(Instant.class)))
                .thenReturn(3L);
        when(repository.deleteByStatusAndCreatedAtBefore(eq(OutboxEventStatus.FAILED), any(Instant.class)))
                .thenReturn(2L);
        OutboxRetentionJob retentionJob = new OutboxRetentionJob(repository, 7, 30);

        retentionJob.deleteExpiredOutboxEvents();

        verify(repository).deleteByStatusAndPublishedAtBefore(eq(OutboxEventStatus.PUBLISHED), any(Instant.class));
        verify(repository).deleteByStatusAndCreatedAtBefore(eq(OutboxEventStatus.FAILED), any(Instant.class));
    }
}
