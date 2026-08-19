package com.highpay.payment.application.port;

import java.util.Optional;
import java.util.UUID;

import com.highpay.payment.application.usecase.ListPaymentsResult;
import com.highpay.payment.domain.model.Payment;

public interface PaymentRepository {

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    ListPaymentsResult findAll(int page, int size);

    Payment save(Payment payment);

}