package com.highpay.processor.infrastructure.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpay.processor.application.model.PaymentCreatedEvent;
import com.highpay.processor.application.model.ProviderPaymentResult;
import com.highpay.processor.application.port.ProviderClient;
import com.highpay.processor.infrastructure.observability.CorrelationId;

@Component
public class HttpProviderClient implements ProviderClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final Duration requestTimeout;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired
    public HttpProviderClient(
            @Value("${highpay.provider.base-url}") String baseUrl,
            @Value("${highpay.provider.request-timeout-ms:3000}") long requestTimeoutMs,
            @Value("${highpay.provider.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${highpay.provider.circuit-breaker.open-duration-ms:10000}") long openDurationMs) {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(requestTimeoutMs))
                .build();
        this.baseUrl = baseUrl;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.circuitBreaker = new SimpleCircuitBreaker("provider", failureThreshold, openDurationMs);
    }

    HttpProviderClient(
            String baseUrl,
            long requestTimeoutMs) {
        this(baseUrl, requestTimeoutMs, 3, 10000);
    }

    @Override
    public ProviderPaymentResult process(PaymentCreatedEvent event) {
        try {
            return circuitBreaker.execute(() -> processWithHttp(event));
        } catch (ProviderHttpException exception) {
            return new ProviderPaymentResult("ERROR", null);
        }
    }

    private ProviderPaymentResult processWithHttp(PaymentCreatedEvent event) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/provider/payments"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrNew())
                .POST(HttpRequest.BodyPublishers.ofString(toJson(event)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderHttpException(response.statusCode());
            }

            ProviderPaymentResponse providerResponse = fromJson(response.body());
            return new ProviderPaymentResult(
                    providerResponse.status(),
                    providerResponse.providerTransactionId());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not call provider", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling provider", exception);
        }
    }

    private String toJson(PaymentCreatedEvent event) {
        ProviderPaymentRequest request = new ProviderPaymentRequest(
                event.paymentId(),
                event.amount(),
                event.currency());

        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize provider request", exception);
        }
    }

    private ProviderPaymentResponse fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, ProviderPaymentResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid provider response payload", exception);
        }
    }

    private record ProviderPaymentRequest(
            UUID paymentId,
            BigDecimal amount,
            String currency) {
    }

    private record ProviderPaymentResponse(
            String status,
            String providerTransactionId,
            String message) {
    }

    private static class ProviderHttpException extends RuntimeException {

        ProviderHttpException(int statusCode) {
            super("Provider returned HTTP " + statusCode);
        }
    }
}
