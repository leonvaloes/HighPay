package com.highpay.payment.application.port;

import com.highpay.payment.domain.model.Payment;

public interface OutboxEventRepository {

    void savePaymentCreatedEvent(Payment payment);

}