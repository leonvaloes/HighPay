package com.highpay.processor.infrastructure.messaging.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.highpay.processor.application.usecase.ProcessPaymentCreatedUseCase;
import com.highpay.processor.infrastructure.observability.CorrelationId;

@Component
public class PaymentCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedListener.class);

    private final ProcessPaymentCreatedUseCase processPaymentCreatedUseCase;

    public PaymentCreatedListener(
            ProcessPaymentCreatedUseCase processPaymentCreatedUseCase) {

        this.processPaymentCreatedUseCase = processPaymentCreatedUseCase;
    }

    @RabbitListener(queues = "${highpay.rabbitmq.payment-created-queue}")
    public void handle(Message message) {
        String payload = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        String correlationId = CorrelationId.normalize(
                (String) message.getMessageProperties().getHeaders().get(CorrelationId.HEADER_NAME));

        MDC.put(CorrelationId.MDC_KEY, correlationId);

        try {
            log.info("payment_created_message_received");
            processPaymentCreatedUseCase.execute(payload);
            log.info("payment_created_message_processed");
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
