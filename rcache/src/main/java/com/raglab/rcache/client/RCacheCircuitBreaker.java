package com.raglab.rcache.client;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight sliding-window fail-open circuit breaker for RCache client.
 * Trips OPEN after threshold failures within a window.
 * Transitions to HALF-OPEN after reset timeout.
 */
public class RCacheCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long resetTimeoutMs;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastStateChangeTimeMs = new AtomicLong(System.currentTimeMillis());
    private volatile State state = State.CLOSED;

    public RCacheCircuitBreaker(int failureThreshold, long resetTimeoutMs) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutMs = resetTimeoutMs;
    }

    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        if (state == State.OPEN) {
            if (now - lastStateChangeTimeMs.get() > resetTimeoutMs) {
                state = State.HALF_OPEN;
                lastStateChangeTimeMs.set(now);
                return true;
            }
            return false;
        }
        return true;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state != State.CLOSED) {
            state = State.CLOSED;
            lastStateChangeTimeMs.set(System.currentTimeMillis());
        }
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold || state == State.HALF_OPEN) {
            state = State.OPEN;
            lastStateChangeTimeMs.set(System.currentTimeMillis());
        }
    }

    public State getState() {
        // Evaluate dynamic transition from OPEN to HALF_OPEN
        if (state == State.OPEN && (System.currentTimeMillis() - lastStateChangeTimeMs.get() > resetTimeoutMs)) {
            state = State.HALF_OPEN;
        }
        return state;
    }

    public void reset() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
        lastStateChangeTimeMs.set(System.currentTimeMillis());
    }
}

