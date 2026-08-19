package com.highpay.processor.infrastructure.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "highpay.processed-events.retention.enabled",
        havingValue = "true")
public class ProcessedEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventRetentionJob.class);

    private final JpaProcessedEventRepository repository;
    private final int processedTtlDays;
    private final int failedTtlDays;

    public ProcessedEventRetentionJob(
            JpaProcessedEventRepository repository,
            @Value("${highpay.processed-events.retention.processed-ttl-days:30}") int processedTtlDays,
            @Value("${highpay.processed-events.retention.failed-ttl-days:90}") int failedTtlDays) {

        this.repository = repository;
        this.processedTtlDays = processedTtlDays;
        this.failedTtlDays = failedTtlDays;
    }

    @Scheduled(fixedDelayString = "${highpay.processed-events.retention.fixed-delay-ms:3600000}")
    @Transactional
    public void deleteExpiredProcessedEvents() {
        Instant processedCutoff = Instant.now().minus(processedTtlDays, ChronoUnit.DAYS);
        Instant failedCutoff = Instant.now().minus(failedTtlDays, ChronoUnit.DAYS);

        long deletedProcessed = repository.deleteByStatusAndProcessedAtBefore(
                ProcessedEventStatus.PROCESSED,
                processedCutoff);
        long deletedFailed = repository.deleteByStatusAndProcessedAtBefore(
                ProcessedEventStatus.FAILED,
                failedCutoff);

        if (deletedProcessed > 0 || deletedFailed > 0) {
            log.info(
                    "processed_event_retention_deleted processed={} failed={}",
                    deletedProcessed,
                    deletedFailed);
        }
    }
}
