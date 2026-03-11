package com.example.user_groupservice.security;

import com.example.common.api.ApiProblemDetailsFactory;
import com.example.user_groupservice.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        String correlationId = resolveCorrelationId(request);
        ProblemDetail body = ApiProblemDetailsFactory.problemDetail(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "access-denied",
            "Access denied",
            "You do not have permission to perform this action",
            request.getRequestURI()
        );
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }
}