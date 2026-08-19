package com.highpay.payment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class PublicApiJwtAuthenticationFilterTest {

    @Test
    void shouldSkipPublicApiAuthWhenDisabled() throws ServletException, IOException {
        PublicApiJwtAuthenticationFilter filter = new PublicApiJwtAuthenticationFilter(false, null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectPublicApiRequestWithoutBearerTokenWhenEnabled() throws ServletException, IOException {
        PublicApiJwtAuthenticationFilter filter = new PublicApiJwtAuthenticationFilter(true, token -> {
            throw new IllegalArgumentException("Missing bearer token");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or missing bearer token");
    }

    @Test
    void shouldAcceptPublicApiRequestWithValidBearerTokenWhenEnabled() throws ServletException, IOException {
        PublicApiJwtAuthenticationFilter filter = new PublicApiJwtAuthenticationFilter(
                true,
                token -> new JwtPrincipal("merchant-001", "payments:read"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("highpay.jwt.principal"))
                .isEqualTo(new JwtPrincipal("merchant-001", "payments:read"));
    }
}
