package com.highpay.payment.infrastructure.persistence.outbox;

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
        name = "highpay.outbox.retention.enabled",
        havingValue = "true")
public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final JpaOutboxEventRepository repository;
    private final int publishedTtlDays;
    private final int failedTtlDays;

    public OutboxRetentionJob(
            JpaOutboxEventRepository repository,
            @Value("${highpay.outbox.retention.published-ttl-days:7}") int publishedTtlDays,
            @Value("${highpay.outbox.retention.failed-ttl-days:30}") int failedTtlDays) {

        this.repository = repository;
        this.publishedTtlDays = publishedTtlDays;
        this.failedTtlDays = failedTtlDays;
    }

    @Scheduled(fixedDelayString = "${highpay.outbox.retention.fixed-delay-ms:3600000}")
    @Transactional
    public void deleteExpiredOutboxEvents() {
        Instant publishedCutoff = Instant.now().minus(publishedTtlDays, ChronoUnit.DAYS);
        Instant failedCutoff = Instant.now().minus(failedTtlDays, ChronoUnit.DAYS);

        long deletedPublished = repository.deleteByStatusAndPublishedAtBefore(
                OutboxEventStatus.PUBLISHED,
                publishedCutoff);
        long deletedFailed = repository.deleteByStatusAndCreatedAtBefore(
                OutboxEventStatus.FAILED,
                failedCutoff);

        if (deletedPublished > 0 || deletedFailed > 0) {
            log.info(
                    "outbox_retention_deleted published={} failed={}",
                    deletedPublished,
                    deletedFailed);
        }
    }
}
