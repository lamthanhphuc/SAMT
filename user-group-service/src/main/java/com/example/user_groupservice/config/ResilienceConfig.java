package com.example.user_groupservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Configuration
 * 
 * Implement Circuit Breaker và Retry cho gRPC calls tới Identity Service
 * 
 * Circuit Breaker Strategy:
 * - Failure rate threshold: 50% (open circuit nếu 50% requests fail)
 * - Wait duration in open state: 10s
 * - Permitted calls in half-open state: 3
 * - Sliding window size: 10 calls
 * 
 * Retry Strategy:
 * - Max attempts: 3
 * - Wait duration: 500ms (exponential backoff)
 * - Retry on: StatusRuntimeException, TimeoutException
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Circuit Breaker Configuration for Identity Service gRPC calls
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                // Failure rate threshold (50%)
                .failureRateThreshold(50)
                
                // Wait duration in open state (10 seconds)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                
                // Permitted number of calls in half-open state
                .permittedNumberOfCallsInHalfOpenState(3)
                
                // Sliding window size (10 calls)
                .slidingWindowSize(10)
                
                // Sliding window type
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                
                // Minimum number of calls before calculating failure rate
                .minimumNumberOfCalls(5)
                
                // Slow call duration threshold (5 seconds)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                
                // Slow call rate threshold (100%)
                .slowCallRateThreshold(100)
                
                // Record exceptions
                .recordExceptions(
                        io.grpc.StatusRuntimeException.class,
                        java.util.concurrent.TimeoutException.class
                )
                
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        
        // Register circuit breaker for Identity Service
        var circuitBreaker = registry.circuitBreaker("identityService");
        
        // Event listeners for debugging
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> 
                        log.warn("Identity Service Circuit Breaker state changed: FROM {} TO {}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> 
                        log.error("Identity Service Circuit Breaker failure rate exceeded: {}%", 
                                event.getFailureRate()))
                .onCallNotPermitted(event -> 
                        log.error("Identity Service Circuit Breaker call not permitted (circuit is OPEN)"));
        
        return registry;
    }

    /**
     * Retry Configuration for Identity Service gRPC calls
     */
    @Bean
public RetryRegistry retryRegistry() {

    RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)

            // Exponential backoff: 500ms → 1000ms → 2000ms
            .intervalFunction(
                    io.github.resilience4j.core.IntervalFunction
                            .ofExponentialBackoff(500, 2.0)
            )

            .retryExceptions(
                    io.grpc.StatusRuntimeException.class,
                    java.util.concurrent.TimeoutException.class
            )

            .ignoreExceptions(
                    IllegalArgumentException.class,
                    IllegalStateException.class
            )

            .build();

    RetryRegistry registry = RetryRegistry.of(config);

    var retry = registry.retry("identityService");

    retry.getEventPublisher()
            .onRetry(event ->
                    log.warn("Identity Service call retry attempt #{}: {}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage()))
            .onSuccess(event ->
                    log.debug("Identity Service call succeeded after {} retry attempts",
                            event.getNumberOfRetryAttempts()))
            .onError(event ->
                    log.error("Identity Service call failed after {} retry attempts",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable()));

    return registry;
}
}
