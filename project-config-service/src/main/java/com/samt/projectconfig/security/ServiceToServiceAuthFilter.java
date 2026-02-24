package com.samt.projectconfig.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Service-to-Service Authentication Filter.
 * 
 * Validates X-Service-Name and X-Service-Key headers for internal API.
 * Only applies to /internal/** endpoints.
 * 
 * SEC-INTERNAL-01: Validate service name
 * SEC-INTERNAL-02: Validate service key
 */
@Component
@Slf4j
public class ServiceToServiceAuthFilter extends OncePerRequestFilter {
    
    @Value("${service-to-service.sync-service.key}")
    private String syncServiceKey;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        
        // Only apply to /internal/** paths
        if (!request.getRequestURI().startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract service authentication headers
        String serviceName = request.getHeader("X-Service-Name");
        String serviceKey = request.getHeader("X-Service-Key");
        
        // SEC-INTERNAL-01: Validate service name
        if (!"sync-service".equals(serviceName)) {
            log.warn("Invalid service name: {}", serviceName);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid service authentication\"}");
            return;
        }
        
        // SEC-INTERNAL-02: Validate service key
        if (!syncServiceKey.equals(serviceKey)) {
            log.warn("Invalid service key for {}", serviceName);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid service authentication\"}");
            return;
        }
        
        log.debug("Service-to-service auth validated for {}", serviceName);
        filterChain.doFilter(request, response);
    }
}
