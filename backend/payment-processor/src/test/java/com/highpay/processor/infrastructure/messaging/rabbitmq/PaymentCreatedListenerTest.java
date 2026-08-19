package com.highpay.processor.infrastructure.messaging.rabbitmq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.highpay.processor.application.usecase.ProcessPaymentCreatedUseCase;
import com.highpay.processor.infrastructure.observability.CorrelationId;

class PaymentCreatedListenerTest {

    @Test
    void shouldDelegatePayloadToUseCase() {
        ProcessPaymentCreatedUseCase useCase = mock(ProcessPaymentCreatedUseCase.class);
        PaymentCreatedListener listener = new PaymentCreatedListener(useCase);
        MessageProperties properties = new MessageProperties();
        properties.setHeader(CorrelationId.HEADER_NAME, "corr-123");
        Message message = new Message("{\"paymentId\":\"123\"}".getBytes(), properties);

        listener.handle(message);

        verify(useCase).execute("{\"paymentId\":\"123\"}");
    }
}
