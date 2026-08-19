package com.highpay.payment.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JwtTokenVerifier implements TokenVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final byte[] secret;

    public JwtTokenVerifier(ObjectMapper objectMapper, Clock clock, String issuer, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Public JWT secret must have at least 32 characters");
        }

        this.objectMapper = objectMapper;
        this.clock = clock;
        this.issuer = issuer;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public JwtPrincipal verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing bearer token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        verifySignature(parts[0], parts[1], parts[2]);

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"JWT".equals(header.getOrDefault("typ", "JWT"))) {
            throw new IllegalArgumentException("Invalid JWT type");
        }
        if (!"HS256".equals(header.get("alg"))) {
            throw new IllegalArgumentException("Unsupported JWT algorithm");
        }

        Map<String, Object> claims = decodeJson(parts[1]);
        String subject = asString(claims.get("sub"));
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Missing JWT subject");
        }

        String actualIssuer = asString(claims.get("iss"));
        if (issuer != null && !issuer.isBlank() && !issuer.equals(actualIssuer)) {
            throw new IllegalArgumentException("Invalid JWT issuer");
        }

        Number expiresAt = asNumber(claims.get("exp"));
        if (expiresAt == null) {
            throw new IllegalArgumentException("Missing JWT expiration");
        }

        Instant expiration = Instant.ofEpochSecond(expiresAt.longValue());
        if (!expiration.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Expired JWT");
        }

        return new JwtPrincipal(subject, asString(claims.get("scope")));
    }

    private void verifySignature(String header, String payload, String signature) {
        String signedContent = header + "." + payload;
        String expected = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hmacSha256(signedContent));

        if (!constantTimeEquals(expected, signature)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
    }

    private byte[] hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not verify JWT signature", exception);
        }
    }

    private Map<String, Object> decodeJson(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(decoded, CLAIMS_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT payload", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);

        if (left.length != right.length) {
            return false;
        }

        int result = 0;
        for (int index = 0; index < left.length; index++) {
            result |= left[index] ^ right[index];
        }

        return result == 0;
    }

    private String asString(Object value) {
        return value instanceof String string ? string : null;
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }
}
