package com.samt.projectconfig.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to implement distributed tracing via correlation ID.
 * 
 * Responsibilities:
 * - Extract X-Request-ID from incoming request headers
 * - Generate UUID if header is missing
 * - Store correlation ID in MDC (Mapped Diagnostic Context)
 * - Add X-Request-ID to response headers
 * - Clean up MDC after request completion
 * 
 * MDC Storage:
 * - Key: "correlationId"
 * - Backed by ThreadLocal (thread-isolated)
 * - Must be cleared to prevent memory leaks
 * - Propagated to async threads via MdcTaskDecorator
 * 
 * Execution Order:
 * - HIGHEST_PRECEDENCE + 1 (runs before security filters)
 * - Ensures correlation ID available for all downstream filters
 * 
 * Thread Safety:
 * - Filter runs on HTTP request thread (Tomcat worker thread)
 * - MDC.put() and MDC.remove() are thread-safe
 * - finally block guarantees cleanup even on exceptions
 * 
 * Integration:
 * - Works with logback pattern: %X{correlationId}
 * - Propagated to gRPC calls via Metadata
 * - Propagated to async threads via TaskDecorator
 * 
 * @author Production Team
 * @version 1.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Request-ID";
    private static final String MDC_KEY = "correlationId";
    
    /**
     * Extract or generate correlation ID and store in MDC.
     * 
     * Flow:
     * 1. Extract X-Request-ID from request header
     * 2. Generate UUID if missing
     * 3. Store in MDC for logging
     * 4. Add to response header for client tracing
     * 5. Process request chain
     * 6. Clean up MDC (prevents memory leak)
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain Filter chain
     * @throws ServletException if filter processing fails
     * @throws IOException if I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Extract correlation ID from request header or generate new one
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Extracted correlation ID from header: {}", correlationId);
        }
        
        // Store in MDC for logging (ThreadLocal storage)
        MDC.put(MDC_KEY, correlationId);
        
        // Add to response header for client-side tracing
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        try {
            // Continue filter chain with correlation ID in MDC
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: Clear MDC to prevent memory leak
            // Tomcat worker threads are pooled and reused
            // ThreadLocal values persist until explicitly removed
            MDC.remove(MDC_KEY);
        }
    }
}
