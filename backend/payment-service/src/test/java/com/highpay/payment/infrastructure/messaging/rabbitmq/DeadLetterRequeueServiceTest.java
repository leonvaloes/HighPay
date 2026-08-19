package com.highpay.payment.infrastructure.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DeadLetterRequeueServiceTest {

    @Test
    void shouldRequeueOneDeadLetterMessageToOriginalExchange() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqProperties properties = rabbitMqProperties();
        Message message = new Message("{}".getBytes(), new MessageProperties());
        DeadLetterRequeueService service = new DeadLetterRequeueService(rabbitTemplate, properties);
        when(rabbitTemplate.receive("highpay.payment-created.dlq")).thenReturn(message);

        boolean requeued = service.requeueOnePaymentCreatedMessage();

        assertThat(requeued).isTrue();
        verify(rabbitTemplate).send("highpay.payments.exchange", "payment.created", message);
    }

    @Test
    void shouldReturnFalseWhenDeadLetterQueueIsEmpty() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        DeadLetterRequeueService service = new DeadLetterRequeueService(rabbitTemplate, rabbitMqProperties());
        when(rabbitTemplate.receive("highpay.payment-created.dlq")).thenReturn(null);

        boolean requeued = service.requeueOnePaymentCreatedMessage();

        assertThat(requeued).isFalse();
        verify(rabbitTemplate).receive("highpay.payment-created.dlq");
        verifyNoMoreInteractions(rabbitTemplate);
    }

    private static RabbitMqProperties rabbitMqProperties() {
        return new RabbitMqProperties(
                "highpay.payments.exchange",
                "payment.created",
                "highpay.payment-created.queue",
                "highpay.payments.dlx",
                "payment.created.dead-letter",
                "highpay.payment-created.dlq");
    }
}
