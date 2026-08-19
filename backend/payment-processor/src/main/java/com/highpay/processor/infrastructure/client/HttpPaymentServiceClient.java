package com.highpay.processor.infrastructure.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpay.processor.application.port.PaymentServiceClient;
import com.highpay.processor.infrastructure.observability.CorrelationId;

@Component
public class HttpPaymentServiceClient implements PaymentServiceClient {

    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final Duration requestTimeout;
    private final String internalAuthToken;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired
    public HttpPaymentServiceClient(
            @Value("${highpay.payment-service.base-url}") String baseUrl,
            @Value("${highpay.payment-service.request-timeout-ms:3000}") long requestTimeoutMs,
            @Value("${highpay.payment-service.internal-auth-token}") String internalAuthToken,
            @Value("${highpay.payment-service.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${highpay.payment-service.circuit-breaker.open-duration-ms:10000}") long openDurationMs,
            @Value("${highpay.payment-service.tls.enabled:false}") boolean tlsEnabled,
            @Value("${highpay.payment-service.tls.key-store:}") String keyStorePath,
            @Value("${highpay.payment-service.tls.key-store-password:}") String keyStorePassword,
            @Value("${highpay.payment-service.tls.key-store-type:PKCS12}") String keyStoreType,
            @Value("${highpay.payment-service.tls.trust-store:}") String trustStorePath,
            @Value("${highpay.payment-service.tls.trust-store-password:}") String trustStorePassword,
            @Value("${highpay.payment-service.tls.trust-store-type:PKCS12}") String trustStoreType) {

        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(requestTimeoutMs));

        if (tlsEnabled) {
            httpClientBuilder.sslContext(sslContext(
                    keyStorePath,
                    keyStorePassword,
                    keyStoreType,
                    trustStorePath,
                    trustStorePassword,
                    trustStoreType));
        }

        this.httpClient = httpClientBuilder.build();
        this.baseUrl = baseUrl;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.internalAuthToken = internalAuthToken;
        this.circuitBreaker = new SimpleCircuitBreaker("payment-service", failureThreshold, openDurationMs);
    }

    HttpPaymentServiceClient(
            String baseUrl,
            long requestTimeoutMs,
            String internalAuthToken) {
        this(baseUrl, requestTimeoutMs, internalAuthToken, 3, 10000, false, "", "", "PKCS12", "", "", "PKCS12");
    }

    HttpPaymentServiceClient(
            String baseUrl,
            long requestTimeoutMs,
            String internalAuthToken,
            int failureThreshold,
            long openDurationMs) {
        this(
                baseUrl,
                requestTimeoutMs,
                internalAuthToken,
                failureThreshold,
                openDurationMs,
                false,
                "",
                "",
                "PKCS12",
                "",
                "",
                "PKCS12");
    }

    @Override
    public void markAsProcessing(UUID paymentId) {
        postWithoutBody("/internal/payments/" + paymentId + "/processing");
    }

    @Override
    public void approve(UUID paymentId, String providerTransactionId) {
        postWithProviderTransactionId(
                "/internal/payments/" + paymentId + "/approve",
                providerTransactionId);
    }

    @Override
    public void reject(UUID paymentId, String providerTransactionId) {
        postWithProviderTransactionId(
                "/internal/payments/" + paymentId + "/reject",
                providerTransactionId);
    }

    @Override
    public void fail(UUID paymentId) {
        postWithoutBody("/internal/payments/" + paymentId + "/fail");
    }

    private void postWithoutBody(String path) {
        circuitBreaker.execute(() -> send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrNew())
                .header(INTERNAL_SERVICE_TOKEN_HEADER, internalAuthToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()));
    }

    private void postWithProviderTransactionId(String path, String providerTransactionId) {
        circuitBreaker.execute(() -> send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrNew())
                .header(INTERNAL_SERVICE_TOKEN_HEADER, internalAuthToken)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(providerTransactionId)))
                .build()));
    }

    private String toJson(String providerTransactionId) {
        try {
            return objectMapper.writeValueAsString(
                    new ProviderTransactionRequest(providerTransactionId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment service request", exception);
        }
    }

    private void send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Payment service returned HTTP " + response.statusCode());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not call payment service", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling payment service", exception);
        }
    }

    private record ProviderTransactionRequest(String providerTransactionId) {
    }

    private SSLContext sslContext(
            String keyStorePath,
            String keyStorePassword,
            String keyStoreType,
            String trustStorePath,
            String trustStorePassword,
            String trustStoreType) {
        try {
            KeyStore keyStore = loadKeyStore(keyStorePath, keyStorePassword, keyStoreType);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

            KeyStore trustStore = loadKeyStore(trustStorePath, trustStorePassword, trustStoreType);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    keyManagerFactory.getKeyManagers(),
                    trustManagerFactory.getTrustManagers(),
                    null);
            return sslContext;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not configure payment service mTLS", exception);
        }
    }

    private KeyStore loadKeyStore(
            String keyStorePath,
            String keyStorePassword,
            String keyStoreType) throws Exception {
        if (keyStorePath == null || keyStorePath.isBlank()) {
            throw new IllegalArgumentException("Key store path must be configured when payment service TLS is enabled");
        }

        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        try (InputStream inputStream = Files.newInputStream(Path.of(keyStorePath))) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }
        return keyStore;
    }
}
