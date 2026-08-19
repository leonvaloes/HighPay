package com.highpay.payment.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.application.exception.PaymentNotFoundException;
import com.highpay.payment.application.port.PaymentMetrics;
import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.model.Payment;

@Service
public class FailPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentMetrics paymentMetrics;

    public FailPaymentUseCase(
            PaymentRepository paymentRepository,
            PaymentMetrics paymentMetrics) {

        this.paymentRepository = paymentRepository;
        this.paymentMetrics = paymentMetrics;
    }

    @Transactional
    public Payment execute(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.fail();
        Payment savedPayment = paymentRepository.save(payment);
        paymentMetrics.recordPaymentFailed();
        return savedPayment;
    }
}