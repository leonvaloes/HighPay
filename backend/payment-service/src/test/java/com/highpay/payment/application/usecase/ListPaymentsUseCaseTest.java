package com.highpay.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;

class ListPaymentsUseCaseTest {

    @Test
    void shouldReturnPaymentsPage() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        ListPaymentsUseCase useCase = new ListPaymentsUseCase(paymentRepository);

        Payment firstPayment = Payment.create(
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX,
                "idem-001");
        Payment secondPayment = Payment.create(
                "merchant-002",
                new BigDecimal("200.00"),
                "BRL",
                PaymentMethod.PIX,
                "idem-002");
        paymentRepository.save(firstPayment);
        paymentRepository.save(secondPayment);

        ListPaymentsResult result = useCase.execute(0, 20);

        assertThat(result.payments())
                .extracting(Payment::getId)
                .containsExactlyInAnyOrder(
                        firstPayment.getId(),
                        secondPayment.getId());
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyPageWhenThereAreNoPayments() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        ListPaymentsUseCase useCase = new ListPaymentsUseCase(paymentRepository);

        ListPaymentsResult result = useCase.execute(0, 20);

        assertThat(result.payments()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void shouldRejectNegativePage() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        ListPaymentsUseCase useCase = new ListPaymentsUseCase(paymentRepository);

        assertThatThrownBy(() -> useCase.execute(-1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page must be greater than or equal to zero");
    }

    @Test
    void shouldRejectInvalidSize() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        ListPaymentsUseCase useCase = new ListPaymentsUseCase(paymentRepository);

        assertThatThrownBy(() -> useCase.execute(0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Size must be between 1 and 100");
    }

    private static class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<UUID, Payment> paymentsById = new HashMap<>();
        private final Map<String, Payment> paymentsByIdempotencyKey = new HashMap<>();

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.ofNullable(paymentsById.get(id));
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(paymentsByIdempotencyKey.get(idempotencyKey));
        }

        @Override
        public ListPaymentsResult findAll(int page, int size) {
            List<Payment> payments = new ArrayList<>(paymentsById.values());
            int fromIndex = Math.min(page * size, payments.size());
            int toIndex = Math.min(fromIndex + size, payments.size());
            List<Payment> pageItems = payments.subList(fromIndex, toIndex);
            int totalPages = payments.isEmpty()
                    ? 0
                    : (int) Math.ceil((double) payments.size() / size);

            return new ListPaymentsResult(
                    pageItems,
                    page,
                    size,
                    payments.size(),
                    totalPages);
        }

        @Override
        public Payment save(Payment payment) {
            paymentsById.put(payment.getId(), payment);
            paymentsByIdempotencyKey.put(payment.getIdempotencyKey(), payment);
            return payment;
        }
    }
}