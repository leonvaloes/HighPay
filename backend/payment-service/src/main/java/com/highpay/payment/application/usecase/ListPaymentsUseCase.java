package com.highpay.payment.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.application.port.PaymentRepository;

@Service
public class ListPaymentsUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository paymentRepository;

    public ListPaymentsUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public ListPaymentsResult execute(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be greater than or equal to zero");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return paymentRepository.findAll(page, size);
    }
}