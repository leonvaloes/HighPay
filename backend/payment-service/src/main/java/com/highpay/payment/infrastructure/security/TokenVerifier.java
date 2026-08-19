package com.highpay.payment.infrastructure.security;

@FunctionalInterface
public interface TokenVerifier {

    JwtPrincipal verify(String token);
}
