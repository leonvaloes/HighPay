package com.highpay.payment.infrastructure.persistence.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventEntity() {
    }

    private OutboxEventEntity(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            OutboxEventStatus status,
            Integer retryCount,
            Instant createdAt) {

        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public static OutboxEventEntity pending(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String payload) {

        return pending(
                UUID.randomUUID(),
                aggregateId,
                aggregateType,
                eventType,
                payload);
    }

    public static OutboxEventEntity pending(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String payload) {

        return new OutboxEventEntity(
                id,
                aggregateId,
                aggregateType,
                eventType,
                payload,
                OutboxEventStatus.PENDING,
                0,
                Instant.now());
    }

    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markAsFailed(int maxRetryAttempts) {
        this.retryCount++;

        if (this.retryCount >= maxRetryAttempts) {
            this.status = OutboxEventStatus.FAILED;
            return;
        }

        this.status = OutboxEventStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}