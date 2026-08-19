package com.highpay.payment.infrastructure.persistence.outbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface JpaOutboxEventRepository
        extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(
            OutboxEventStatus status,
            Pageable pageable);
    @Query(value = """
            SELECT *
              FROM outbox_events
             WHERE status = 'PENDING'
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForPublishing(@Param("limit") int limit);
    long deleteByStatusAndPublishedAtBefore(OutboxEventStatus status, Instant publishedAt);
    long deleteByStatusAndCreatedAtBefore(OutboxEventStatus status, Instant createdAt);
    long countByStatus(OutboxEventStatus status);
}
