package com.highpay.processor.infrastructure.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ProcessedEventRetentionJobTest {

    @Test
    void shouldDeleteProcessedAndFailedEventsOlderThanConfiguredTtl() {
        JpaProcessedEventRepository repository = mock(JpaProcessedEventRepository.class);
        when(repository.deleteByStatusAndProcessedAtBefore(eq(ProcessedEventStatus.PROCESSED), any(Instant.class)))
                .thenReturn(3L);
        when(repository.deleteByStatusAndProcessedAtBefore(eq(ProcessedEventStatus.FAILED), any(Instant.class)))
                .thenReturn(2L);
        ProcessedEventRetentionJob retentionJob = new ProcessedEventRetentionJob(repository, 30, 90);

        retentionJob.deleteExpiredProcessedEvents();

        verify(repository).deleteByStatusAndProcessedAtBefore(eq(ProcessedEventStatus.PROCESSED), any(Instant.class));
        verify(repository).deleteByStatusAndProcessedAtBefore(eq(ProcessedEventStatus.FAILED), any(Instant.class));
    }
}
