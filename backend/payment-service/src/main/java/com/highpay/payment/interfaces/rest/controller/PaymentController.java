package com.highpay.payment.interfaces.rest.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.highpay.payment.application.usecase.CreatePaymentResult;
import com.highpay.payment.application.usecase.CreatePaymentUseCase;
import com.highpay.payment.application.usecase.GetPaymentUseCase;
import com.highpay.payment.application.usecase.ListPaymentsResult;
import com.highpay.payment.application.usecase.ListPaymentsUseCase;
import com.highpay.payment.interfaces.rest.request.CreatePaymentRequest;
import com.highpay.payment.interfaces.rest.response.PaymentPageResponse;
import com.highpay.payment.interfaces.rest.response.PaymentResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final CreatePaymentUseCase createPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final ListPaymentsUseCase listPaymentsUseCase;

    public PaymentController(
            CreatePaymentUseCase createPaymentUseCase,
            GetPaymentUseCase getPaymentUseCase,
            ListPaymentsUseCase listPaymentsUseCase) {

        this.createPaymentUseCase = createPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.listPaymentsUseCase = listPaymentsUseCase;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        CreatePaymentResult result = createPaymentUseCase.execute(
                idempotencyKey,
                request.merchantId(),
                request.amount(),
                request.currency(),
                request.paymentMethod());

        log.info(
                "payment_create_request_processed paymentId={} merchantId={} status={} created={}",
                result.payment().getId(),
                request.merchantId(),
                result.payment().getStatus(),
                result.created());

        PaymentResponse response =
                PaymentResponse.from(result.payment());

        if (result.created()) {
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PaymentPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ListPaymentsResult result = listPaymentsUseCase.execute(page, size);
        log.info(
                "payment_list_request_processed page={} size={} totalElements={}",
                page,
                size,
                result.totalElements());

        return ResponseEntity.ok(PaymentPageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getById(
            @PathVariable UUID id) {

        return getPaymentUseCase.execute(id)
                .map(payment -> {
                    log.info("payment_get_request_processed paymentId={} status={}", id, payment.getStatus());
                    return payment;
                })
                .map(PaymentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.info("payment_get_request_not_found paymentId={}", id);
                    return ResponseEntity.notFound().build();
                });
    }
}
