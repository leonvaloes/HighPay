package com.highpay.processor.infrastructure.persistence;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JpaProcessedEventRepository
        extends JpaRepository<ProcessedEventEntity, UUID> {
    long deleteByStatusAndProcessedAtBefore(ProcessedEventStatus status, Instant processedAt);
}
