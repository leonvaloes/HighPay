package com.highpay.payment.application.usecase;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.application.port.OutboxEventRepository;
import com.highpay.payment.application.port.PaymentMetrics;
import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;

@Service
public class CreatePaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMetrics paymentMetrics;

    public CreatePaymentUseCase(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentMetrics paymentMetrics) {

        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentMetrics = paymentMetrics;
    }

    @Transactional
    public CreatePaymentResult execute(
            String idempotencyKey,
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        Optional<Payment> existingPayment = paymentRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            if (!payment.hasSameCreationRequest(
                    merchantId,
                    amount,
                    currency,
                    paymentMethod)) {
                throw new IllegalStateException(
                        "Idempotency key was already used with a different payment request");
            }

            paymentMetrics.recordIdempotencyHit();

            return new CreatePaymentResult(
                    payment,
                    false);
        }

        Payment payment = Payment.create(
                merchantId,
                amount,
                currency,
                paymentMethod,
                idempotencyKey);

        Payment savedPayment = paymentRepository.save(payment);
        outboxEventRepository.savePaymentCreatedEvent(savedPayment);
        paymentMetrics.recordPaymentCreated();

        return new CreatePaymentResult(
                savedPayment,
                true);
    }
}