package com.example.identityservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UnsupportedHttpMethodFilter extends OncePerRequestFilter {

    private static final Set<String> UNSUPPORTED_METHODS = Set.of("TRACE", "QUERY");
    private static final String ALLOW_HEADER = "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (UNSUPPORTED_METHODS.contains(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", ALLOW_HEADER);
            response.setContentLength(0);
            return;
        }

        filterChain.doFilter(request, response);
    }
}