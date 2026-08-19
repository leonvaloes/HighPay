package com.highpay.payment.interfaces.rest.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.highpay.payment.application.usecase.ApprovePaymentUseCase;
import com.highpay.payment.application.usecase.FailPaymentUseCase;
import com.highpay.payment.application.usecase.MarkPaymentAsProcessingUseCase;
import com.highpay.payment.application.usecase.RejectPaymentUseCase;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;
import com.highpay.payment.infrastructure.security.InternalServiceAuthenticationFilter;
import com.highpay.payment.interfaces.rest.exception.GlobalExceptionHandler;

class InternalPaymentControllerTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalPaymentController controller = new InternalPaymentController(
                mock(MarkPaymentAsProcessingUseCase.class),
                mock(ApprovePaymentUseCase.class),
                mock(RejectPaymentUseCase.class),
                mock(FailPaymentUseCase.class));

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(new InternalServiceAuthenticationFilter(INTERNAL_TOKEN))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUnauthorizedWhenInternalTokenIsMissing() throws Exception {
        mockMvc.perform(post("/internal/payments/11111111-1111-1111-1111-111111111111/processing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnUnauthorizedWhenInternalTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/internal/payments/11111111-1111-1111-1111-111111111111/processing")
                        .header(InternalServiceAuthenticationFilter.HEADER_NAME, "invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowMtlsInternalRequestWhenClientCertificateIsPresent() throws Exception {
        UUID paymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        MarkPaymentAsProcessingUseCase markPaymentAsProcessingUseCase = mock(MarkPaymentAsProcessingUseCase.class);
        Payment payment = createPayment("idem-mtls");
        payment.markAsProcessing();
        when(markPaymentAsProcessingUseCase.execute(eq(paymentId)))
                .thenReturn(payment);

        InternalPaymentController controller = new InternalPaymentController(
                markPaymentAsProcessingUseCase,
                mock(ApprovePaymentUseCase.class),
                mock(RejectPaymentUseCase.class),
                mock(FailPaymentUseCase.class));
        MockMvc mtlsMockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilters(new InternalServiceAuthenticationFilter("mtls", ""))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mtlsMockMvc.perform(requestWithClientCertificate(
                        "/internal/payments/11111111-1111-1111-1111-111111111111/processing"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBadRequestWhenApproveProviderTransactionIdIsBlank() throws Exception {
        mockMvc.perform(post("/internal/payments/11111111-1111-1111-1111-111111111111/approve")
                        .header(InternalServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerTransactionId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("providerTransactionId"));
    }

    @Test
    void shouldReturnBadRequestWhenRejectProviderTransactionIdIsBlank() throws Exception {
        mockMvc.perform(post("/internal/payments/11111111-1111-1111-1111-111111111111/reject")
                        .header(InternalServiceAuthenticationFilter.HEADER_NAME, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerTransactionId": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("providerTransactionId"));
    }

    private static RequestBuilder requestWithClientCertificate(String path) {
        return requestContext -> {
            MockHttpServletRequest request = post(path).buildRequest(requestContext);
            request.setAttribute(
                    "jakarta.servlet.request.X509Certificate",
                    new java.security.cert.X509Certificate[] { mock(java.security.cert.X509Certificate.class) });
            return request;
        };
    }

    private Payment createPayment(String idempotencyKey) {
        return Payment.create(
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX,
                idempotencyKey);
    }
}
