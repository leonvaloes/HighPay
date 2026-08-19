package com.highpay.payment.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.enums.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(
            UUID id,
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String idempotencyKey,
            String requestFingerprint,
            PaymentStatus status,
            Instant createdAt,
            Instant updatedAt) {

        this.id = id;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payment create(
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String idempotencyKey) {

        validateCreationRequest(merchantId, amount, currency, paymentMethod, idempotencyKey);

        Instant now = Instant.now();

        return new Payment(
                UUID.randomUUID(),
                merchantId,
                amount,
                currency,
                paymentMethod,
                idempotencyKey,
                requestFingerprint(merchantId, amount, currency, paymentMethod),
                PaymentStatus.CREATED,
                now,
                now);
    }

    public boolean hasSameCreationRequest(
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        if (requestFingerprint == null || requestFingerprint.isBlank()) {
            return hasSameStoredCreationFields(merchantId, amount, currency, paymentMethod);
        }

        return requestFingerprint.equals(
                requestFingerprint(merchantId, amount, currency, paymentMethod));
    }

    public void markAsProcessing() {

        if (status == PaymentStatus.PROCESSING) {
            return;
        }

        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException(
                    "Payment must be CREATED to start processing");
        }

        this.status = PaymentStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void approve(String providerTransactionId) {

        validateProviderTransaction(providerTransactionId);

        if (status == PaymentStatus.APPROVED && hasSameProviderTransaction(providerTransactionId)) {
            return;
        }

        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment must be PROCESSING to be approved");
        }

        this.status = PaymentStatus.APPROVED;
        this.providerTransactionId = providerTransactionId;
        this.updatedAt = Instant.now();
    }

    public void reject(String providerTransactionId) {

        validateProviderTransaction(providerTransactionId);

        if (status == PaymentStatus.REJECTED && hasSameProviderTransaction(providerTransactionId)) {
            return;
        }

        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment must be PROCESSING to be rejected");
        }

        this.status = PaymentStatus.REJECTED;
        this.providerTransactionId = providerTransactionId;
        this.updatedAt = Instant.now();
    }

    public void fail() {

        if (status == PaymentStatus.FAILED) {
            return;
        }

        if (status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payment must be PROCESSING to fail");
        }

        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    private boolean hasSameStoredCreationFields(
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        return this.merchantId.trim().equals(merchantId.trim())
                && this.amount.compareTo(amount) == 0
                && this.currency.trim().equalsIgnoreCase(currency.trim())
                && this.paymentMethod == paymentMethod;
    }

    private boolean hasSameProviderTransaction(String providerTransactionId) {
        if (this.providerTransactionId == null || providerTransactionId == null) {
            return this.providerTransactionId == providerTransactionId;
        }

        return this.providerTransactionId.equals(providerTransactionId);
    }

    private static void validateProviderTransaction(String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new IllegalArgumentException("Provider transaction id is required");
        }
    }

    private static void validateCreationRequest(
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod,
            String idempotencyKey) {

        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("Merchant is required");
        }

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }

    private static String requestFingerprint(
            String merchantId,
            BigDecimal amount,
            String currency,
            PaymentMethod paymentMethod) {

        String canonicalRequest = merchantId.trim()
                + "|" + amount.stripTrailingZeros().toPlainString()
                + "|" + currency.trim().toUpperCase()
                + "|" + paymentMethod.name();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}