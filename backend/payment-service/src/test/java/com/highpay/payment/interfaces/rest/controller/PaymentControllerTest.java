package com.highpay.payment.interfaces.rest.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.highpay.payment.application.usecase.CreatePaymentResult;
import com.highpay.payment.application.usecase.CreatePaymentUseCase;
import com.highpay.payment.application.usecase.GetPaymentUseCase;
import com.highpay.payment.application.usecase.ListPaymentsResult;
import com.highpay.payment.application.usecase.ListPaymentsUseCase;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;
import com.highpay.payment.interfaces.rest.exception.GlobalExceptionHandler;

class PaymentControllerTest {

    private MockMvc mockMvc;
    private CreatePaymentUseCase createPaymentUseCase;
    private GetPaymentUseCase getPaymentUseCase;
    private ListPaymentsUseCase listPaymentsUseCase;

    @BeforeEach
    void setUp() {
        createPaymentUseCase = mock(CreatePaymentUseCase.class);
        getPaymentUseCase = mock(GetPaymentUseCase.class);
        listPaymentsUseCase = mock(ListPaymentsUseCase.class);

        PaymentController paymentController = new PaymentController(
                createPaymentUseCase,
                getPaymentUseCase,
                listPaymentsUseCase);

        mockMvc = MockMvcBuilders
                .standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnCreatedWhenPaymentIsCreated() throws Exception {
        Payment payment = createPayment("idem-001");
        when(createPaymentUseCase.execute(
                eq("idem-001"),
                eq("merchant-001"),
                eq(new BigDecimal("100.00")),
                eq("BRL"),
                eq(PaymentMethod.PIX)))
                .thenReturn(new CreatePaymentResult(payment, true));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "merchant-001",
                                  "amount": 100.00,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "",
                                  "amount": 0,
                                  "currency": "brl",
                                  "paymentMethod": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("merchantId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("amount")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("currency")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("paymentMethod")));
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "merchant-001",
                                  "amount": 100.00,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Missing required header: Idempotency-Key"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
    }

    @Test
    void shouldReturnBadRequestWhenPaymentIdIsInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/payments/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/not-a-uuid"));
    }

    @Test
    void shouldReturnPaymentById() throws Exception {
        Payment payment = createPayment("idem-001");
        when(getPaymentUseCase.execute(payment.getId()))
                .thenReturn(Optional.of(payment));

        mockMvc.perform(get("/api/v1/payments/{id}", payment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.merchantId").value("merchant-001"));
    }

    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(getPaymentUseCase.execute(paymentId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnPaymentList() throws Exception {
        Payment firstPayment = createPayment("idem-001");
        Payment secondPayment = createPayment("idem-002");
        when(listPaymentsUseCase.execute(0, 20))
                .thenReturn(new ListPaymentsResult(
                        List.of(firstPayment, secondPayment),
                        0,
                        20,
                        2,
                        1));

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(firstPayment.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(secondPayment.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyWasUsedWithDifferentPayload() throws Exception {
        when(createPaymentUseCase.execute(
                eq("idem-001"),
                eq("merchant-001"),
                eq(new BigDecimal("200.00")),
                eq("BRL"),
                eq(PaymentMethod.PIX)))
                .thenThrow(new IllegalStateException(
                        "Idempotency key was already used with a different payment request"));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantId": "merchant-001",
                                  "amount": 200.00,
                                  "currency": "BRL",
                                  "paymentMethod": "PIX"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Idempotency key was already used with a different payment request"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
    }
    @Test
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionHappens() throws Exception {
        when(listPaymentsUseCase.execute(0, 20))
                .thenThrow(new RuntimeException("Database unavailable"));

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected internal error"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"));
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