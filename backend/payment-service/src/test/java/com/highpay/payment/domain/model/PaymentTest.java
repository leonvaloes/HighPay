package com.highpay.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.enums.PaymentStatus;

class PaymentTest {

    @Test
    void shouldAllowRepeatedProcessingTransition() {
        Payment payment = newPayment();

        payment.markAsProcessing();
        payment.markAsProcessing();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void shouldAllowRepeatedApproveWithSameProviderTransaction() {
        Payment payment = newPayment();
        payment.markAsProcessing();

        payment.approve("provider-001");
        payment.approve("provider-001");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("provider-001");
    }

    @Test
    void shouldRejectApproveWithDifferentProviderTransactionAfterApproval() {
        Payment payment = newPayment();
        payment.markAsProcessing();
        payment.approve("provider-001");

        assertThatThrownBy(() -> payment.approve("provider-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment must be PROCESSING to be approved");
    }

    @Test
    void shouldAllowRepeatedRejectWithSameProviderTransaction() {
        Payment payment = newPayment();
        payment.markAsProcessing();

        payment.reject("provider-001");
        payment.reject("provider-001");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("provider-001");
    }

    @Test
    void shouldRejectRejectWithDifferentProviderTransactionAfterRejection() {
        Payment payment = newPayment();
        payment.markAsProcessing();
        payment.reject("provider-001");

        assertThatThrownBy(() -> payment.reject("provider-002"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment must be PROCESSING to be rejected");
    }


    @Test
    void shouldRequireProviderTransactionIdWhenApproving() {
        Payment payment = newPayment();
        payment.markAsProcessing();

        assertThatThrownBy(() -> payment.approve(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider transaction id is required");
    }

    @Test
    void shouldRequireProviderTransactionIdWhenRejecting() {
        Payment payment = newPayment();
        payment.markAsProcessing();

        assertThatThrownBy(() -> payment.reject(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Provider transaction id is required");
    }
    @Test
    void shouldAllowRepeatedFailTransition() {
        Payment payment = newPayment();
        payment.markAsProcessing();

        payment.fail();
        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldNotAllowProcessingAfterFinalStatus() {
        Payment payment = newPayment();
        payment.markAsProcessing();
        payment.approve("provider-001");

        assertThatThrownBy(payment::markAsProcessing)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment must be CREATED to start processing");
    }

    private Payment newPayment() {
        return Payment.create(
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX,
                "idem-001");
    }
}