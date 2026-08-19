package com.highpay.payment.infrastructure.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PublicApiJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_PATH_PREFIX = "/api/v1/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PRINCIPAL_ATTRIBUTE = "highpay.jwt.principal";

    private final boolean enabled;
    private final TokenVerifier tokenVerifier;

    @Autowired
    public PublicApiJwtAuthenticationFilter(
            @Value("${highpay.public-auth.enabled:false}") boolean enabled,
            @Value("${highpay.public-auth.jwt-secret:}") String jwtSecret,
            @Value("${highpay.public-auth.issuer:highpay-local}") String issuer) {

        this.enabled = enabled;
        this.tokenVerifier = enabled
                ? new JwtTokenVerifier(new ObjectMapper(), Clock.systemUTC(), issuer, jwtSecret)
                : null;
    }

    PublicApiJwtAuthenticationFilter(boolean enabled, TokenVerifier tokenVerifier) {
        this.enabled = enabled;
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractBearerToken(request);
            JwtPrincipal principal = tokenVerifier.verify(token);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":401,"error":"Unauthorized","message":"Invalid or missing bearer token"}
                    """);
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Missing bearer token");
        }

        return authorization.substring(BEARER_PREFIX.length());
    }
}
