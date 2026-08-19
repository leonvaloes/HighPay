package com.highpay.payment.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.application.exception.PaymentNotFoundException;
import com.highpay.payment.application.port.PaymentMetrics;
import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.model.Payment;

@Service
public class ApprovePaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentMetrics paymentMetrics;

    public ApprovePaymentUseCase(
            PaymentRepository paymentRepository,
            PaymentMetrics paymentMetrics) {

        this.paymentRepository = paymentRepository;
        this.paymentMetrics = paymentMetrics;
    }

    @Transactional
    public Payment execute(UUID paymentId, String providerTransactionId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.approve(providerTransactionId);
        Payment savedPayment = paymentRepository.save(payment);
        paymentMetrics.recordPaymentApproved();
        return savedPayment;
    }
}