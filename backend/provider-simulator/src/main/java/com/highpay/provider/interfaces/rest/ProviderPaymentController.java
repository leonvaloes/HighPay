package com.highpay.provider.interfaces.rest;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/provider/payments")
public class ProviderPaymentController {

    private static final Logger log = LoggerFactory.getLogger(ProviderPaymentController.class);

    private final String defaultScenario;
    private final long slowDelayMs;
    private final long timeoutDelayMs;

    public ProviderPaymentController(
            @Value("${highpay.provider.default-scenario:SUCCESS}") String defaultScenario,
            @Value("${highpay.provider.slow-delay-ms:2000}") long slowDelayMs,
            @Value("${highpay.provider.timeout-delay-ms:10000}") long timeoutDelayMs) {

        this.defaultScenario = defaultScenario;
        this.slowDelayMs = slowDelayMs;
        this.timeoutDelayMs = timeoutDelayMs;
    }

    @PostMapping
    public ResponseEntity<ProviderPaymentResponse> process(
            @RequestHeader(name = "X-Provider-Scenario", required = false) String scenario,
            @Valid @RequestBody ProviderPaymentRequest request) throws InterruptedException {

        String selectedScenario = scenario == null || scenario.isBlank()
                ? defaultScenario
                : scenario;

        log.info(
                "provider_payment_request_received paymentId={} scenario={}",
                request.paymentId(),
                selectedScenario);

        return switch (selectedScenario.toUpperCase()) {
            case "SUCCESS" -> ResponseEntity.ok(new ProviderPaymentResponse(
                    "SUCCESS",
                    "provider-" + UUID.randomUUID(),
                    "Payment approved by provider"));
            case "REJECTED" -> ResponseEntity.ok(new ProviderPaymentResponse(
                    "REJECTED",
                    "provider-" + UUID.randomUUID(),
                    "Payment rejected by provider"));
            case "ERROR" -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ProviderPaymentResponse(
                            "ERROR",
                            null,
                            "Provider internal error"));
            case "SLOW" -> {
                Thread.sleep(slowDelayMs);
                yield ResponseEntity.ok(new ProviderPaymentResponse(
                        "SUCCESS",
                        "provider-" + UUID.randomUUID(),
                        "Payment approved slowly by provider"));
            }
            case "TIMEOUT" -> {
                Thread.sleep(timeoutDelayMs);
                yield ResponseEntity.ok(new ProviderPaymentResponse(
                        "SUCCESS",
                        "provider-" + UUID.randomUUID(),
                        "Payment approved after timeout scenario"));
            }
            default -> ResponseEntity.badRequest().body(new ProviderPaymentResponse(
                    "ERROR",
                    null,
                    "Unknown provider scenario"));
        };
    }
}
