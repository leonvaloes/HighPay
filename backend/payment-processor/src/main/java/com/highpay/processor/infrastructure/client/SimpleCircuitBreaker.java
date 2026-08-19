package com.highpay.processor.infrastructure.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class SimpleCircuitBreaker {

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private int consecutiveFailures;
    private Instant openUntil;

    public SimpleCircuitBreaker(
            String name,
            int failureThreshold,
            long openDurationMs) {
        this(name, failureThreshold, Duration.ofMillis(openDurationMs), Clock.systemUTC());
    }

    SimpleCircuitBreaker(
            String name,
            int failureThreshold,
            Duration openDuration,
            Clock clock) {

        if (failureThreshold < 1) {
            throw new IllegalArgumentException("Circuit breaker failure threshold must be greater than zero");
        }

        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public synchronized <T> T execute(Supplier<T> operation) {
        rejectIfOpen();

        try {
            T result = operation.get();
            consecutiveFailures = 0;
            openUntil = null;
            return result;
        } catch (RuntimeException exception) {
            recordFailure();
            throw exception;
        }
    }

    public synchronized void execute(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    public synchronized boolean isOpen() {
        return openUntil != null && Instant.now(clock).isBefore(openUntil);
    }

    private void rejectIfOpen() {
        if (isOpen()) {
            throw new IllegalStateException("Circuit breaker is open for " + name);
        }
    }

    private void recordFailure() {
        consecutiveFailures++;

        if (consecutiveFailures >= failureThreshold) {
            openUntil = Instant.now(clock).plus(openDuration);
        }
    }
}
