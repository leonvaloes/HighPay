package com.highpay.provider.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProviderPaymentControllerTest {

    @Test
    void shouldApprovePaymentWhenScenarioIsSuccess() throws Exception {
        ProviderPaymentController controller = new ProviderPaymentController("SUCCESS", 1, 1);

        ResponseEntity<ProviderPaymentResponse> response = controller.process(
                "SUCCESS",
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("SUCCESS");
        assertThat(response.getBody().providerTransactionId()).isNotBlank();
    }

    @Test
    void shouldRejectPaymentWhenScenarioIsRejected() throws Exception {
        ProviderPaymentController controller = new ProviderPaymentController("SUCCESS", 1, 1);

        ResponseEntity<ProviderPaymentResponse> response = controller.process(
                "REJECTED",
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("REJECTED");
    }

    @Test
    void shouldReturnServerErrorWhenScenarioIsError() throws Exception {
        ProviderPaymentController controller = new ProviderPaymentController("SUCCESS", 1, 1);

        ResponseEntity<ProviderPaymentResponse> response = controller.process(
                "ERROR",
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo("ERROR");
    }

    private ProviderPaymentRequest request() {
        return new ProviderPaymentRequest(
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "BRL");
    }
}