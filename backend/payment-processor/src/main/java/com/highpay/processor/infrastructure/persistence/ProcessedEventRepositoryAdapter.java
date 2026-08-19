package com.highpay.processor.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.processor.application.model.ProviderPaymentResult;
import com.highpay.processor.application.port.ProcessedEventRepository;

@Repository
public class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventRepository jpaProcessedEventRepository;

    public ProcessedEventRepositoryAdapter(JpaProcessedEventRepository jpaProcessedEventRepository) {
        this.jpaProcessedEventRepository = jpaProcessedEventRepository;
    }

    @Override
    @Transactional
    public boolean tryStartProcessing(UUID eventId, UUID paymentId) {
        Optional<ProcessedEventEntity> existingEvent = jpaProcessedEventRepository.findById(eventId);

        if (existingEvent.isPresent()) {
            ProcessedEventEntity event = existingEvent.get();

            if (event.isProcessed() || event.isProcessing()) {
                return false;
            }

            event.markAsProcessing();
            return true;
        }

        try {
            jpaProcessedEventRepository.saveAndFlush(ProcessedEventEntity.processing(eventId, paymentId));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderPaymentResult> findProviderResult(UUID eventId) {
        return jpaProcessedEventRepository.findById(eventId)
                .filter(ProcessedEventEntity::hasProviderResult)
                .map(event -> new ProviderPaymentResult(
                        event.getProviderStatus(),
                        event.getProviderTransactionId()));
    }

    @Override
    @Transactional
    public void saveProviderResult(UUID eventId, ProviderPaymentResult result) {
        ProcessedEventEntity event = findRequiredEvent(eventId);
        event.saveProviderResult(result.status(), result.providerTransactionId());
    }

    @Override
    @Transactional
    public void markAsProcessed(UUID eventId) {
        ProcessedEventEntity event = findRequiredEvent(eventId);
        event.markAsProcessed();
    }

    @Override
    @Transactional
    public void markAsFailed(UUID eventId) {
        ProcessedEventEntity event = findRequiredEvent(eventId);
        event.markAsFailed();
    }

    private ProcessedEventEntity findRequiredEvent(UUID eventId) {
        return jpaProcessedEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Processed event not found: " + eventId));
    }
}