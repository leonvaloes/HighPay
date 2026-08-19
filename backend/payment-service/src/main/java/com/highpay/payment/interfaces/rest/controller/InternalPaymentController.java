package com.highpay.payment.interfaces.rest.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.highpay.payment.application.usecase.ApprovePaymentUseCase;
import com.highpay.payment.application.usecase.FailPaymentUseCase;
import com.highpay.payment.application.usecase.MarkPaymentAsProcessingUseCase;
import com.highpay.payment.application.usecase.RejectPaymentUseCase;
import com.highpay.payment.interfaces.rest.request.ProviderTransactionRequest;
import com.highpay.payment.interfaces.rest.response.PaymentResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private static final Logger log = LoggerFactory.getLogger(InternalPaymentController.class);

    private final MarkPaymentAsProcessingUseCase markPaymentAsProcessingUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final RejectPaymentUseCase rejectPaymentUseCase;
    private final FailPaymentUseCase failPaymentUseCase;

    public InternalPaymentController(
            MarkPaymentAsProcessingUseCase markPaymentAsProcessingUseCase,
            ApprovePaymentUseCase approvePaymentUseCase,
            RejectPaymentUseCase rejectPaymentUseCase,
            FailPaymentUseCase failPaymentUseCase) {

        this.markPaymentAsProcessingUseCase = markPaymentAsProcessingUseCase;
        this.approvePaymentUseCase = approvePaymentUseCase;
        this.rejectPaymentUseCase = rejectPaymentUseCase;
        this.failPaymentUseCase = failPaymentUseCase;
    }

    @PostMapping("/{id}/processing")
    public ResponseEntity<PaymentResponse> markAsProcessing(@PathVariable UUID id) {
        PaymentResponse response = PaymentResponse.from(markPaymentAsProcessingUseCase.execute(id));
        log.info("internal_payment_marked_processing paymentId={} status={}", id, response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody ProviderTransactionRequest request) {

        PaymentResponse response = PaymentResponse.from(
                approvePaymentUseCase.execute(id, request.providerTransactionId()));
        log.info("internal_payment_approved paymentId={} providerTransactionId={}", id, request.providerTransactionId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PaymentResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody ProviderTransactionRequest request) {

        PaymentResponse response = PaymentResponse.from(
                rejectPaymentUseCase.execute(id, request.providerTransactionId()));
        log.info("internal_payment_rejected paymentId={} providerTransactionId={}", id, request.providerTransactionId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<PaymentResponse> fail(@PathVariable UUID id) {
        PaymentResponse response = PaymentResponse.from(failPaymentUseCase.execute(id));
        log.info("internal_payment_failed paymentId={} status={}", id, response.status());
        return ResponseEntity.ok(response);
    }
}
