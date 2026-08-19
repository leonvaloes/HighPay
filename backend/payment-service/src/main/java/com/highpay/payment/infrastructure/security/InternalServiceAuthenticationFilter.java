package com.highpay.payment.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Service-Token";

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String CLIENT_CERTIFICATES_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    private final String mode;
    private final String expectedToken;

    @Autowired
    public InternalServiceAuthenticationFilter(
            @Value("${highpay.internal-auth.mode:token}") String mode,
            @Value("${highpay.internal-auth.token}") String expectedToken) {
        if (!"token".equals(mode) && !"mtls".equals(mode)) {
            throw new IllegalArgumentException("Internal authentication mode must be token or mtls");
        }

        if ("token".equals(mode) && (expectedToken == null || expectedToken.isBlank())) {
            throw new IllegalArgumentException("Internal authentication token must be configured");
        }

        this.mode = mode;
        this.expectedToken = expectedToken;
    }

    public InternalServiceAuthenticationFilter(String expectedToken) {
        this("token", expectedToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!isAuthenticated(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        if ("mtls".equals(mode)) {
            return hasClientCertificate(request);
        }

        return matchesExpectedToken(request.getHeader(HEADER_NAME));
    }

    private boolean hasClientCertificate(HttpServletRequest request) {
        Object certificates = request.getAttribute(CLIENT_CERTIFICATES_ATTRIBUTE);

        if (!(certificates instanceof X509Certificate[] x509Certificates)) {
            return false;
        }

        return x509Certificates.length > 0;
    }

    private boolean matchesExpectedToken(String actualToken) {
        if (actualToken == null || actualToken.isBlank()) {
            return false;
        }

        byte[] actual = actualToken.getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(actual, expected);
    }
}
