package com.highpay.payment.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterRequeueService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties rabbitMqProperties;

    public DeadLetterRequeueService(
            RabbitTemplate rabbitTemplate,
            RabbitMqProperties rabbitMqProperties) {

        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqProperties = rabbitMqProperties;
    }

    public boolean requeueOnePaymentCreatedMessage() {
        Message message = rabbitTemplate.receive(rabbitMqProperties.paymentCreatedDeadLetterQueue());

        if (message == null) {
            return false;
        }

        rabbitTemplate.send(
                rabbitMqProperties.paymentExchange(),
                rabbitMqProperties.paymentCreatedRoutingKey(),
                message);

        return true;
    }
}
