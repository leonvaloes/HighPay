package com.highpay.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.application.usecase.ListPaymentsResult;
import com.highpay.payment.domain.model.Payment;

@Repository
public class PaymentRepositoryAdapter
        implements PaymentRepository {

    private final JpaPaymentRepository jpaPaymentRepository;

    public PaymentRepositoryAdapter(
            JpaPaymentRepository jpaPaymentRepository) {

        this.jpaPaymentRepository = jpaPaymentRepository;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpaPaymentRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(
            String idempotencyKey) {

        return jpaPaymentRepository
                .findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public ListPaymentsResult findAll(int page, int size) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Payment> paymentsPage = jpaPaymentRepository.findAll(pageRequest);

        return new ListPaymentsResult(
                paymentsPage.getContent(),
                paymentsPage.getNumber(),
                paymentsPage.getSize(),
                paymentsPage.getTotalElements(),
                paymentsPage.getTotalPages());
    }

    @Override
    public Payment save(Payment payment) {

        return jpaPaymentRepository.save(payment);
    }

}