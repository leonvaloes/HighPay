package com.highpay.payment.infrastructure.messaging.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "highpay.rabbitmq")
public record RabbitMqProperties(
        String paymentExchange,
        String paymentCreatedRoutingKey,
        String paymentCreatedQueue,
        String deadLetterExchange,
        String paymentCreatedDeadLetterRoutingKey,
        String paymentCreatedDeadLetterQueue
) {
}