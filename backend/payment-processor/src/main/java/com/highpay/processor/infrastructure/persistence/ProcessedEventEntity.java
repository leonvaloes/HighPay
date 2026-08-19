package com.highpay.processor.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProcessedEventStatus status;

    @Column(name = "provider_status", length = 30)
    private String providerStatus;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    private ProcessedEventEntity(
            UUID eventId,
            UUID paymentId,
            ProcessedEventStatus status,
            Instant processedAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.status = status;
        this.processedAt = processedAt;
    }

    public static ProcessedEventEntity processing(UUID eventId, UUID paymentId) {
        return new ProcessedEventEntity(eventId, paymentId, ProcessedEventStatus.PROCESSING, Instant.now());
    }

    public void markAsProcessing() {
        this.status = ProcessedEventStatus.PROCESSING;
        this.processedAt = Instant.now();
    }

    public void saveProviderResult(String providerStatus, String providerTransactionId) {
        this.providerStatus = providerStatus;
        this.providerTransactionId = providerTransactionId;
        this.processedAt = Instant.now();
    }

    public void markAsProcessed() {
        this.status = ProcessedEventStatus.PROCESSED;
        this.processedAt = Instant.now();
    }

    public void markAsFailed() {
        this.status = ProcessedEventStatus.FAILED;
        this.processedAt = Instant.now();
    }

    public boolean isProcessed() {
        return status == ProcessedEventStatus.PROCESSED;
    }

    public boolean isProcessing() {
        return status == ProcessedEventStatus.PROCESSING;
    }

    public boolean hasProviderResult() {
        return providerStatus != null && !providerStatus.isBlank();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public ProcessedEventStatus getStatus() {
        return status;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}