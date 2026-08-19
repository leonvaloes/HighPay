package com.highpay.processor.application.port;

import com.highpay.processor.application.model.PaymentCreatedEvent;
import com.highpay.processor.application.model.ProviderPaymentResult;

public interface ProviderClient {

    ProviderPaymentResult process(PaymentCreatedEvent event);
}