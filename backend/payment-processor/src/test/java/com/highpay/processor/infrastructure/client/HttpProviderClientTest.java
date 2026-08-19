package com.highpay.processor.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.highpay.processor.application.model.PaymentCreatedEvent;
import com.highpay.processor.application.model.ProviderPaymentResult;
import com.sun.net.httpserver.HttpServer;

class HttpProviderClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendProviderRequestAsJsonAndParseResponseAsJson() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/provider/payments", exchange -> {
            capturedRequest.method = exchange.getRequestMethod();
            capturedRequest.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = "{ \"message\": \"ok\", \"providerTransactionId\": \"provider-123\", \"status\": \"SUCCESS\" }"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpProviderClient client = new HttpProviderClient(baseUrl(), 3000);
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "corr-123",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new BigDecimal("100.00"),
                "BRL");

        ProviderPaymentResult result = client.process(event);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.providerTransactionId()).isEqualTo("provider-123");
        assertThat(capturedRequest.method).isEqualTo("POST");
        assertThat(capturedRequest.contentType).contains("application/json");
        assertThat(capturedRequest.body).contains("\"paymentId\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(capturedRequest.body).contains("\"amount\":100.00");
        assertThat(capturedRequest.body).contains("\"currency\":\"BRL\"");
    }

    @Test
    void shouldReturnErrorResultWhenProviderReturnsNonSuccessHttpStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/provider/payments", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpProviderClient client = new HttpProviderClient(baseUrl(), 3000);
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                UUID.randomUUID(),
                "corr-123",
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "BRL");

        ProviderPaymentResult result = client.process(event);

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.providerTransactionId()).isNull();
    }

    @Test
    void shouldOpenCircuitAfterProviderFailures() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/provider/payments", exchange -> {
            requests.incrementAndGet();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpProviderClient client = new HttpProviderClient(baseUrl(), 3000, 1, 10000);
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                UUID.randomUUID(),
                "corr-123",
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "BRL");

        ProviderPaymentResult firstResult = client.process(event);

        assertThat(firstResult.status()).isEqualTo("ERROR");
        assertThatThrownBy(() -> client.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Circuit breaker is open for provider");
        assertThat(requests).hasValue(1);
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static class CapturedRequest {
        private String method;
        private String contentType;
        private String body;
    }
}
