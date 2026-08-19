package com.highpay.payment.infrastructure.security;

public record JwtPrincipal(String subject, String scope) {
}
