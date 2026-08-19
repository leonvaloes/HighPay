package com.highpay.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.infrastructure.persistence.outbox.JpaOutboxEventRepository;

@SpringBootTest
@Transactional
class CreatePaymentUseCaseIntegrationTest {

    @Autowired
    private CreatePaymentUseCase createPaymentUseCase;

    @Autowired
    private JpaOutboxEventRepository jpaOutboxEventRepository;

    @Test
    void shouldPersistPaymentAndOutboxEventInSameTransaction() {
        long outboxEventsBefore = jpaOutboxEventRepository.count();
        String idempotencyKey = "idem-" + UUID.randomUUID();

        CreatePaymentResult result = createPaymentUseCase.execute(
                idempotencyKey,
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX);

        assertThat(result.created()).isTrue();
        assertThat(jpaOutboxEventRepository.count()).isEqualTo(outboxEventsBefore + 1);
    }
}