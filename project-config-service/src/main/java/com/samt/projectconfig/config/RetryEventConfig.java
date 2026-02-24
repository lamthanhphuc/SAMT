package com.samt.projectconfig.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.retry.event.RetryOnSuccessEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Retry event logging configuration for production observability.
 * 
 * Subscribes to retry events to provide real-time visibility into retry patterns
 * and flaky external dependencies. This addresses ENTERPRISE_ISSUES.md M-03.
 * 
 * Logs emitted:
 * - WARN on retry attempt
 * - INFO on success after retry (only if retried)
 * - WARN on retry exhaustion (only when attempts == maxAttempts)
 * 
 * Applied to:
 * - verificationRetry instance (maxAttempts=2, waitDuration=100ms)
 * 
 * Purpose:
 * - Detect flaky external APIs (Jira, GitHub)
 * - Inform retry configuration tuning
 * - Support troubleshooting through log aggregation
 * - Enable SRE alerting on high retry rates
 * 
 * Note: Retry exhaustion uses WARN level instead of ERROR because not all
 * retry failures are system-level errors (e.g., 404, business validation, 400).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RetryEventConfig {
    
    private final RetryRegistry retryRegistry;
    
    /**
     * Subscribes to retry events after Spring context initialization.
     * Must run after RetryRegistry bean creation.
     * 
     * Uses safe retry retrieval to avoid creating retry instances with default config
     * if they don't exist in application.yml configuration.
     */
    @PostConstruct
    public void configureRetryEventLogging() {
        log.debug("Configuring retry event logging for verificationRetry");
        
        configureRetryEvents("verificationRetry");
        
        log.debug("Retry event logging configured successfully");
    }
    
    /**
     * Safely configures event logging for a specific retry instance if it exists.
     * 
     * @param retryName Name of the retry instance
     */
    private void configureRetryEvents(String retryName) {
        retryRegistry.getAllRetries()
            .stream()
            .filter(retry -> retry.getName().equals(retryName))
            .findFirst()
            .ifPresentOrElse(
                retry -> {
                    retry.getEventPublisher()
                        .onRetry(this::logRetryAttempt)
                        .onSuccess(this::logRetrySuccess)
                        .onError(this::logRetryError);
                    log.debug("Retry event logging enabled for: {}", retryName);
                },
                () -> log.warn("Retry not found in registry, skipping event config: {}", retryName)
            );
    }
    
    /**
     * Logs retry attempts with error information.
     * Uses structured logging format for better log aggregation and parsing.
     * 
     * @param event RetryOnRetryEvent containing retry attempt details
     */
    private void logRetryAttempt(RetryOnRetryEvent event) {
        int maxAttempts = event.getRetry().getRetryConfig().getMaxAttempts();
        
        log.warn("RETRY_ATTEMPT name={} attempt={}/{} error={}", 
            event.getName(),
            event.getNumberOfRetryAttempts(),
            maxAttempts,
            event.getLastThrowable().getClass().getSimpleName());
    }
    
    /**
     * Logs successful operation after retry attempts.
     * Only logs if actual retries occurred (attempts > 0).
     * 
     * @param event RetryOnSuccessEvent containing success details
     */
    private void logRetrySuccess(RetryOnSuccessEvent event) {
        if (event.getNumberOfRetryAttempts() > 0) {
            log.info("RETRY_SUCCESS name={} attempts={}", 
                event.getName(),
                event.getNumberOfRetryAttempts());
        }
    }
    
    /**
     * Logs retry errors and exhaustion when all attempts are consumed.
     * Only logs RETRY_EXHAUSTED when attempts reach maxAttempts.
     * Uses WARN level as retry exhaustion may not always indicate system-level errors
     * (e.g., 404 responses, business validation failures, 400 errors).
     * 
     * @param event RetryOnErrorEvent containing error details
     */
    private void logRetryError(RetryOnErrorEvent event) {
        int maxAttempts = event.getRetry().getRetryConfig().getMaxAttempts();
        
        // Only log when retry is truly exhausted (all attempts consumed)
        if (event.getNumberOfRetryAttempts() >= maxAttempts) {
            log.warn("RETRY_EXHAUSTED name={} attempts={} error={}", 
                event.getName(),
                event.getNumberOfRetryAttempts(),
                event.getLastThrowable().getClass().getSimpleName());
        }
    }
}
