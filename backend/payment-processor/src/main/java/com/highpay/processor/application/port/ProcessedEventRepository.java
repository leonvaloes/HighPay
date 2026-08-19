package com.highpay.processor.application.port;

import java.util.Optional;
import java.util.UUID;

import com.highpay.processor.application.model.ProviderPaymentResult;

public interface ProcessedEventRepository {

    boolean tryStartProcessing(UUID eventId, UUID paymentId);

    Optional<ProviderPaymentResult> findProviderResult(UUID eventId);

    void saveProviderResult(UUID eventId, ProviderPaymentResult result);

    void markAsProcessed(UUID eventId);

    void markAsFailed(UUID eventId);

}