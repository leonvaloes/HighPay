package com.highpay.payment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternalServiceAuthenticationFilterTest {

    @Test
    void shouldRejectBlankExpectedTokenConfiguration() {
        assertThatThrownBy(() -> new InternalServiceAuthenticationFilter("token", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Internal authentication token must be configured");
    }

    @Test
    void shouldRejectInvalidAuthenticationMode() {
        assertThatThrownBy(() -> new InternalServiceAuthenticationFilter("invalid", "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Internal authentication mode must be token or mtls");
    }

    @Test
    void shouldAllowBlankTokenWhenModeIsMtls() {
        new InternalServiceAuthenticationFilter("mtls", "");
    }
}
