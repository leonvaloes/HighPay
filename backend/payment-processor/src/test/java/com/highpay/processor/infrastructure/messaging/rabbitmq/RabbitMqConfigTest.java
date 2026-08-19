package com.highpay.processor.infrastructure.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitMqConfigTest {

    private final RabbitMqConfig config = new RabbitMqConfig();
    private final RabbitMqProperties properties = new RabbitMqProperties(
            "highpay.payments.exchange",
            "payment.created",
            "highpay.payment-created.queue",
            "highpay.payments.dlx",
            "payment.created.dead-letter",
            "highpay.payment-created.dlq");

    @Test
    void shouldConfigurePaymentCreatedQueueWithDeadLetterRouting() {
        Queue queue = config.paymentCreatedQueue(properties);

        assertThat(queue.getName()).isEqualTo("highpay.payment-created.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "highpay.payments.dlx")
                .containsEntry("x-dead-letter-routing-key", "payment.created.dead-letter");
    }

    @Test
    void shouldConfigurePaymentCreatedDeadLetterQueue() {
        Queue queue = config.paymentCreatedDeadLetterQueue(properties);

        assertThat(queue.getName()).isEqualTo("highpay.payment-created.dlq");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).isEmpty();
    }
}