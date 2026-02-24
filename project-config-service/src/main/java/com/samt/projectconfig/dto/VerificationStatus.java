package com.samt.projectconfig.dto;

/**
 * Structured verification status codes for external API verification.
 * 
 * Provides semantic classification of verification results to enable:
 * - Accurate circuit breaker failure detection
 * - Client-side retry logic decisions
 * - Monitoring and alerting differentiation
 * - Audit trail with meaningful status codes
 * 
 * Circuit Breaker Counting Rules:
 * - FAILED_EXTERNAL_DEPENDENCY → Count as failure ✓
 * - FAILED_TIMEOUT → Count as failure ✓
 * - FAILED_SERVER_ERROR → Count as failure ✓
 * - FAILED_INVALID_CREDENTIAL → Ignore (config error) ✗
 * - FAILED_NOT_FOUND → Ignore (config error) ✗
 * - FAILED_BAD_REQUEST → Ignore (config error) ✗
 * - FAILED_RATE_LIMITED → Count as failure (external throttle) ✓
 * - FAILED_CIRCUIT_OPEN → Ignore (resilience state) ✗
 * - FAILED_BULKHEAD_FULL → Ignore (capacity limit) ✗
 */
public enum VerificationStatus {
    
    /**
     * Verification succeeded - API responded correctly.
     */
    SUCCESS("SUCCESS"),
    
    // ============================================
    // CLIENT ERRORS (4xx) - Configuration Issues
    // Should NOT count as circuit breaker failures
    // ============================================
    
    /**
     * Invalid credentials (401/403).
     * Action: User must update configuration with valid credentials.
     * Circuit Breaker: Ignore (not a dependency failure).
     */
    FAILED_INVALID_CREDENTIAL("FAILED_INVALID_CREDENTIAL"),
    
    /**
     * Resource not found (404).
     * Action: User must fix URL/repository name.
     * Circuit Breaker: Ignore (not a dependency failure).
     */
    FAILED_NOT_FOUND("FAILED_NOT_FOUND"),
    
    /**
     * Bad request (400).
     * Action: Check request format.
     * Circuit Breaker: Ignore (not a dependency failure).
     */
    FAILED_BAD_REQUEST("FAILED_BAD_REQUEST"),
    
    // ============================================
    // SERVER/NETWORK ERRORS - Dependency Issues
    // SHOULD count as circuit breaker failures
    // ============================================
    
    /**
     * External API unavailable (503) or connection refused.
     * Action: Wait and retry. External service is down.
     * Circuit Breaker: Count as failure (dependency issue).
     */
    FAILED_EXTERNAL_DEPENDENCY("FAILED_EXTERNAL_DEPENDENCY"),
    
    /**
     * Request timeout (read or connect timeout).
     * Action: Retry. May be transient network issue or API overload.
     * Circuit Breaker: Count as failure (latency degradation).
     */
    FAILED_TIMEOUT("FAILED_TIMEOUT"),
    
    /**
     * Server error (500/502/504).
     * Action: Retry. External API experiencing errors.
     * Circuit Breaker: Count as failure (dependency issue).
     */
    FAILED_SERVER_ERROR("FAILED_SERVER_ERROR"),
    
    /**
     * Rate limited by external API (429).
     * Action: Backoff and retry later.
     * Circuit Breaker: Count as failure (external throttle).
     */
    FAILED_RATE_LIMITED("FAILED_RATE_LIMITED"),
    
    // ============================================
    // RESILIENCE STATES - System Capacity
    // Should NOT count as circuit breaker failures
    // ============================================
    
    /**
     * Circuit breaker is open (fail-fast protection).
     * Action: Wait for circuit to close, then retry.
     * Circuit Breaker: N/A (circuit already managing state).
     */
    FAILED_CIRCUIT_OPEN("FAILED_CIRCUIT_OPEN"),
    
    /**
     * Bulkhead is full (system at capacity).
     * Action: Retry later when capacity available.
     * Circuit Breaker: Ignore (not a dependency failure, just rate limiting).
     */
    FAILED_BULKHEAD_FULL("FAILED_BULKHEAD_FULL");
    
    private final String value;
    
    VerificationStatus(String value) {
        this.value = value;
    }
    
    /**
     * Get string value for DTO serialization.
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Check if status represents a success.
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
    
    /**
     * Check if status should be counted as circuit breaker failure.
     * 
     * Only dependency failures (5xx, timeout, rate limit) are counted.
     * Configuration errors (4xx) and resilience states are ignored.
     */
    public boolean shouldCountAsCircuitFailure() {
        return this == FAILED_EXTERNAL_DEPENDENCY
            || this == FAILED_TIMEOUT
            || this == FAILED_SERVER_ERROR
            || this == FAILED_RATE_LIMITED;
    }
    
    /**
     * Check if status is retryable.
     * 
     * Transient failures (5xx, timeout) can be retried.
     * Configuration errors (4xx) should not be retried automatically.
     */
    public boolean isRetryable() {
        return this == FAILED_EXTERNAL_DEPENDENCY
            || this == FAILED_TIMEOUT
            || this == FAILED_SERVER_ERROR
            || this == FAILED_RATE_LIMITED
            || this == FAILED_CIRCUIT_OPEN
            || this == FAILED_BULKHEAD_FULL;
    }
    
    /**
     * Check if status represents a configuration error.
     * These require user intervention to fix.
     */
    public boolean isConfigurationError() {
        return this == FAILED_INVALID_CREDENTIAL
            || this == FAILED_NOT_FOUND
            || this == FAILED_BAD_REQUEST;
    }
    
    /**
     * Determine verification status from HTTP status code.
     */
    public static VerificationStatus fromHttpStatus(int statusCode) {
        return switch (statusCode) {
            case 200, 201, 202, 204 -> SUCCESS;
            case 400 -> FAILED_BAD_REQUEST;
            case 401, 403 -> FAILED_INVALID_CREDENTIAL;
            case 404 -> FAILED_NOT_FOUND;
            case 429 -> FAILED_RATE_LIMITED;
            case 500, 502 -> FAILED_SERVER_ERROR;
            case 503 -> FAILED_EXTERNAL_DEPENDENCY;
            case 504 -> FAILED_TIMEOUT;
            default -> statusCode >= 500 ? FAILED_SERVER_ERROR : FAILED_BAD_REQUEST;
        };
    }
    
    @Override
    public String toString() {
        return value;
    }
}
