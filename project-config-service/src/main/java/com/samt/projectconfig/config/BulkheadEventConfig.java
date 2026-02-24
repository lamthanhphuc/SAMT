package com.samt.projectconfig.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadOnCallRejectedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Bulkhead event logging configuration for production observability.
 * 
 * Subscribes to bulkhead saturation events to provide real-time visibility
 * into capacity exhaustion. This addresses ENTERPRISE_ISSUES.md M-02.
 * 
 * Logs emitted:
 * - WARN on bulkhead rejection (CALL_REJECTED event)
 * - Includes saturation percentage and capacity metrics
 * 
 * Applied to:
 * - jiraVerification bulkhead (max 100 concurrent calls)
 * - githubVerification bulkhead (max 100 concurrent calls)
 * 
 * Purpose:
 * - Enable SRE log-based alerting on capacity exhaustion
 * - Support troubleshooting via log aggregation
 * - Inform horizontal scaling decisions
 * - Optimize bulkhead configuration tuning
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BulkheadEventConfig {
    
    private final BulkheadRegistry bulkheadRegistry;
    
    /**
     * Subscribes to bulkhead rejection events after Spring context initialization.
     * Must run after BulkheadRegistry bean creation.
     * 
     * Uses safe bulkhead retrieval to avoid creating bulkheads with default config
     * if they don't exist in application.yml configuration.
     */
    @PostConstruct
    public void configureBulkheadEventLogging() {
        log.debug("Configuring bulkhead event logging for jiraVerification and githubVerification");
        
        configureBulkheadEvents("jiraVerification");
        configureBulkheadEvents("githubVerification");
        
        log.debug("Bulkhead event logging configured successfully");
    }
    
    /**
     * Safely configures event logging for a specific bulkhead if it exists.
     * 
     * @param bulkheadName Name of the bulkhead instance
     */
    private void configureBulkheadEvents(String bulkheadName) {
        bulkheadRegistry.getAllBulkheads()
            .stream()
            .filter(bulkhead -> bulkhead.getName().equals(bulkheadName))
            .findFirst()
            .ifPresentOrElse(
                bulkhead -> {
                    bulkhead.getEventPublisher().onCallRejected(this::logSaturation);
                    log.debug("Bulkhead event logging enabled for: {}", bulkheadName);
                },
                () -> log.warn("Bulkhead not found in registry, skipping event config: {}", bulkheadName)
            );
    }
    
    /**
     * Logs bulkhead saturation with capacity metrics when a call is rejected.
     * Uses structured logging format for better log aggregation and parsing.
     * 
     * @param event BulkheadOnCallRejectedEvent containing rejection details
     */
    private void logSaturation(BulkheadOnCallRejectedEvent event) {
        int available = event.getBulkhead().getMetrics().getAvailableConcurrentCalls();
        int max = event.getBulkhead().getBulkheadConfig().getMaxConcurrentCalls();
        int used = max - available;
        float saturation = (1 - (float)available / max) * 100;
        
        log.warn("BULKHEAD_SATURATED name={} used={}/{} saturation={}%", 
            event.getBulkheadName(), used, max, String.format("%.1f", saturation));
    }
}
