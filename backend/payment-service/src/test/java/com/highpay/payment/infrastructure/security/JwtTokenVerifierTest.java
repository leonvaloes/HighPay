package com.highpay.payment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class JwtTokenVerifierTest {

    private static final String SECRET = "test-public-auth-secret-with-at-least-32-chars";
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void shouldVerifyValidJwt() {
        JwtTokenVerifier verifier = new JwtTokenVerifier(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "highpay-test",
                SECRET);

        JwtPrincipal principal = verifier.verify(token("""
                {"sub":"merchant-001","iss":"highpay-test","scope":"payments:write","exp":1787144400}
                """));

        assertThat(principal.subject()).isEqualTo("merchant-001");
        assertThat(principal.scope()).isEqualTo("payments:write");
    }

    @Test
    void shouldRejectExpiredJwt() {
        JwtTokenVerifier verifier = new JwtTokenVerifier(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "highpay-test",
                SECRET);

        String token = token("""
                {"sub":"merchant-001","iss":"highpay-test","exp":1}
                """);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expired JWT");
    }

    @Test
    void shouldRejectInvalidSignature() {
        JwtTokenVerifier verifier = new JwtTokenVerifier(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "highpay-test",
                SECRET);

        String token = token("""
                {"sub":"merchant-001","iss":"highpay-test","exp":1787144400}
                """) + "broken";

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JWT signature");
    }

    private String token(String payloadJson) {
        String header = encode("""
                {"alg":"HS256","typ":"JWT"}
                """);
        String payload = encode(payloadJson);
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    private String encode(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
