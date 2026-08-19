package com.highpay.processor.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class SimpleCircuitBreakerTest {

    @Test
    void shouldOpenAfterFailureThreshold() {
        MutableClock clock = new MutableClock();
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(
                "provider",
                2,
                Duration.ofSeconds(10),
                clock);

        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new IllegalStateException("failure 1");
        })).hasMessage("failure 1");

        assertThat(circuitBreaker.isOpen()).isFalse();

        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new IllegalStateException("failure 2");
        })).hasMessage("failure 2");

        assertThat(circuitBreaker.isOpen()).isTrue();
        assertThatThrownBy(() -> circuitBreaker.execute(() -> "ok"))
                .hasMessage("Circuit breaker is open for provider");
    }

    @Test
    void shouldAllowCallsAgainAfterOpenDuration() {
        MutableClock clock = new MutableClock();
        SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(
                "provider",
                1,
                Duration.ofSeconds(10),
                clock);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> circuitBreaker.execute(() -> {
            throw new IllegalStateException("failure");
        })).hasMessage("failure");

        clock.advance(Duration.ofSeconds(11));

        String result = circuitBreaker.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(1);
        assertThat(circuitBreaker.isOpen()).isFalse();
    }

    private static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-08-18T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
