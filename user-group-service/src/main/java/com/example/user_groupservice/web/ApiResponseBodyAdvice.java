package com.example.user_groupservice.web;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiResponseFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
            || !(response instanceof ServletServerHttpResponse servletResponse)) {
            return body;
        }

        String path = servletRequest.getServletRequest().getRequestURI();
        if (body == null || body instanceof ApiResponse<?> || shouldSkip(path)) {
            return body;
        }

        int status = servletResponse.getServletResponse().getStatus();
        if (status < 200 || status >= 300) {
            return body;
        }

        return ApiResponseFactory.success(status, body, path, resolveCorrelationId(servletRequest));
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/.well-known");
    }

    private String resolveCorrelationId(ServletServerHttpRequest request) {
        String correlationId = request.getServletRequest().getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }
}