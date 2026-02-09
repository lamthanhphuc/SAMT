package com.fpt.projectconfig.security;

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
 * Service-to-service authentication filter
 * Validate X-Service-Name và X-Service-Key headers
 * Chỉ áp dụng cho /internal/** endpoints
 */
@Component
@Slf4j
public class ServiceToServiceAuthFilter extends OncePerRequestFilter {

    private final String expectedServiceKey;

    public ServiceToServiceAuthFilter(@Value("${security.internal-service-key}") String serviceKey) {
        this.expectedServiceKey = serviceKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceName = request.getHeader("X-Service-Name");
        String serviceKey = request.getHeader("X-Service-Key");

        if (serviceName == null || serviceKey == null || !expectedServiceKey.equals(serviceKey)) {
            log.warn("Invalid service auth for path: {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid service authentication\"}}");
            return;
        }

        log.debug("Service authenticated: {}", serviceName);
        filterChain.doFilter(request, response);
    }
}
