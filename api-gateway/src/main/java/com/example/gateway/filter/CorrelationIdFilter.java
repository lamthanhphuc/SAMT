package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filter to add correlation ID (X-Request-ID) for request tracing across services
 */
@Slf4j
@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public int getOrder() {
        return OrderedFilters.CORRELATION_ID; // Should run early in filter chain
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Get existing correlation ID or generate new one
        String correlationId = getOrGenerateCorrelationId(request);
        
        // Add correlation ID to request headers for downstream services
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, correlationId)
                .build();
        
        // Add correlation ID to response headers for client visibility
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, correlationId);
        
        // Add to MDC for logging context
        try {
            org.slf4j.MDC.put("requestId", correlationId);
            
            // Log request with correlation ID
            log.info("Processing request: {} {} [{}]", 
                    request.getMethod(), 
                    request.getURI().getPath(), 
                    correlationId);
            
            // Continue with mutated request
            return chain.filter(exchange.mutate().request(mutatedRequest).build())
                    .doFinally(signalType -> {
                        // Clean up MDC after request processing
                        org.slf4j.MDC.remove("requestId");
                        
                        log.debug("Completed request: {} {} [{}] - {}", 
                                request.getMethod(), 
                                request.getURI().getPath(), 
                                correlationId,
                                signalType);
                    });
                    
        } catch (Exception e) {
            // Ensure MDC is cleaned up even if error occurs
            org.slf4j.MDC.remove("requestId");
            log.error("Error in correlation ID filter for request [{}]: {}", correlationId, e.getMessage());
            throw e;
        }
    }

    /**
     * Get existing correlation ID from headers or generate a new one
     */
    private String getOrGenerateCorrelationId(ServerHttpRequest request) {
        String existingId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        
        if (existingId != null && !existingId.trim().isEmpty() && isValidCorrelationId(existingId)) {
            log.debug("Using existing correlation ID: {}", existingId);
            return existingId.trim();
        }
        
        // Generate new correlation ID
        String newId = generateCorrelationId();
        log.debug("Generated new correlation ID: {}", newId);
        return newId;
    }

    /**
     * Generate a new unique correlation ID
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
    }

    /**
     * Validate correlation ID format (basic validation)
     */
    private boolean isValidCorrelationId(String correlationId) {
        // Basic validation: alphanumeric, reasonable length
        return correlationId.matches("^[a-zA-Z0-9-]{8,36}$");
    }
}