package com.highpay.processor.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class HttpPaymentServiceClientTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendApproveRequestAsJson() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/payments/11111111-1111-1111-1111-111111111111/approve", exchange -> {
            capturedRequest.method = exchange.getRequestMethod();
            capturedRequest.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            capturedRequest.internalToken = exchange.getRequestHeaders().getFirst(INTERNAL_TOKEN_HEADER);
            capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpPaymentServiceClient client = new HttpPaymentServiceClient(baseUrl(), 3000, INTERNAL_TOKEN);

        client.approve(UUID.fromString("11111111-1111-1111-1111-111111111111"), "provider-123");

        assertThat(capturedRequest.method).isEqualTo("POST");
        assertThat(capturedRequest.contentType).contains("application/json");
        assertThat(capturedRequest.internalToken).isEqualTo(INTERNAL_TOKEN);
        assertThat(capturedRequest.body).isEqualTo("{\"providerTransactionId\":\"provider-123\"}");
    }

    @Test
    void shouldSendInternalTokenWhenRequestHasNoBody() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/payments/11111111-1111-1111-1111-111111111111/processing", exchange -> {
            capturedRequest.method = exchange.getRequestMethod();
            capturedRequest.internalToken = exchange.getRequestHeaders().getFirst(INTERNAL_TOKEN_HEADER);
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpPaymentServiceClient client = new HttpPaymentServiceClient(baseUrl(), 3000, INTERNAL_TOKEN);

        client.markAsProcessing(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        assertThat(capturedRequest.method).isEqualTo("POST");
        assertThat(capturedRequest.internalToken).isEqualTo(INTERNAL_TOKEN);
    }

    @Test
    void shouldThrowWhenPaymentServiceReturnsNonSuccessHttpStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/payments/11111111-1111-1111-1111-111111111111/fail", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(409, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpPaymentServiceClient client = new HttpPaymentServiceClient(baseUrl(), 3000, INTERNAL_TOKEN);

        assertThatThrownBy(() -> client.fail(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment service returned HTTP 409");
    }

    @Test
    void shouldOpenCircuitAfterPaymentServiceFailures() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/payments/11111111-1111-1111-1111-111111111111/fail", exchange -> {
            requests.incrementAndGet();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpPaymentServiceClient client = new HttpPaymentServiceClient(baseUrl(), 3000, INTERNAL_TOKEN, 1, 10000);

        assertThatThrownBy(() -> client.fail(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment service returned HTTP 500");
        assertThatThrownBy(() -> client.fail(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Circuit breaker is open for payment-service");
        assertThat(requests).hasValue(1);
    }


    @Test
    void shouldThrowWhenPaymentServiceRequestTimesOut() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/payments/11111111-1111-1111-1111-111111111111/processing", exchange -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        HttpPaymentServiceClient client = new HttpPaymentServiceClient(baseUrl(), 50, INTERNAL_TOKEN);

        assertThatThrownBy(() -> client.markAsProcessing(UUID.fromString("11111111-1111-1111-1111-111111111111")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not call payment service");
    }
    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static class CapturedRequest {
        private String method;
        private String contentType;
        private String internalToken;
        private String body;
    }
}
