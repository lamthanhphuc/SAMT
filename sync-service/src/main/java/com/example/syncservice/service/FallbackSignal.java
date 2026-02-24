package com.example.syncservice.service;

import org.springframework.stereotype.Component;

/**
 * Thread-local signal for fallback execution detection.
 * 
 * CRITICAL: Used to communicate from fallback methods to orchestrator
 * that degraded execution occurred (circuit breaker open or retry exhausted).
 * 
 * Pattern:
 * 1. Acquire signal at method entry (defensive clear)
 * 2. Fallback method calls setDegraded(true, errorMessage)
 * 3. Orchestrator checks isDegraded() after API call
 * 4. AutoCloseable ensures ThreadLocal cleared on scope exit
 * 
 * Thread Safety:
 * - Uses ThreadLocal, safe within same thread
 * - Compatible with @Async due to MDC propagation pattern
 * - AutoCloseable prevents ThreadLocal leaks in thread pools
 */
@Component
public class FallbackSignal implements AutoCloseable {

    private static final ThreadLocal<DegradedExecution> degradedExecution = new ThreadLocal<>();

    /**
     * Mark current execution as degraded (fallback triggered).
     */
    public void setDegraded(boolean degraded, String reason) {
        if (degraded) {
            degradedExecution.set(new DegradedExecution(true, reason));
        } else {
            degradedExecution.remove();
        }
    }

    /**
     * Check if current execution is degraded.
     */
    public boolean isDegraded() {
        DegradedExecution exec = degradedExecution.get();
        return exec != null && exec.degraded;
    }

    /**
     * Get degradation reason.
     */
    public String getReason() {
        DegradedExecution exec = degradedExecution.get();
        return exec != null ? exec.reason : null;
    }

    /**
     * Clear degraded signal (MUST call in finally block).
     */
    public void clear() {
        degradedExecution.remove();
    }

    /**
     * Acquire signal for execution scope.
     * Performs defensive cleanup to prevent ThreadLocal leaks.
     * 
     * Usage:
     * <pre>
     * try (FallbackSignal signal = fallbackSignal.acquire()) {
     *     // sync logic
     * }
     * </pre>
     * 
     * @return this instance for try-with-resources pattern
     */
    public FallbackSignal acquire() {
        degradedExecution.remove(); // Defensive clear
        return this;
    }

    /**
     * AutoCloseable implementation.
     * Guarantees ThreadLocal cleanup even if exception occurs before finally block.
     */
    @Override
    public void close() {
        degradedExecution.remove();
    }

    private static class DegradedExecution {
        final boolean degraded;
        final String reason;

        DegradedExecution(boolean degraded, String reason) {
            this.degraded = degraded;
            this.reason = reason;
        }
    }
}
