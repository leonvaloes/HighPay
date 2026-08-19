package com.highpay.payment.interfaces.rest.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.highpay.payment.infrastructure.messaging.rabbitmq.DeadLetterRequeueService;
import com.highpay.payment.infrastructure.security.InternalServiceAuthenticationFilter;

class InternalRabbitMqControllerTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private DeadLetterRequeueService deadLetterRequeueService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        deadLetterRequeueService = mock(DeadLetterRequeueService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalRabbitMqController(deadLetterRequeueService))
                .addFilters(new InternalServiceAuthenticationFilter(INTERNAL_TOKEN))
                .build();
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() throws Exception {
        mockMvc.perform(post("/internal/rabbitmq/payment-created-dlq/requeue-one"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRequeueOneDeadLetterMessage() throws Exception {
        when(deadLetterRequeueService.requeueOnePaymentCreatedMessage()).thenReturn(true);

        mockMvc.perform(post("/internal/rabbitmq/payment-created-dlq/requeue-one")
                        .header(InternalServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue").value("payment-created-dlq"))
                .andExpect(jsonPath("$.requeued").value(true));
    }
}
