package com.highpay.payment.application.usecase;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.model.Payment;

@Service
public class GetPaymentUseCase {

    private final PaymentRepository paymentRepository;

    public GetPaymentUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> execute(UUID id) {
        return paymentRepository.findById(id);
    }
}