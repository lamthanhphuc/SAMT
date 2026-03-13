package com.example.gateway.error;

import com.example.common.api.ApiProblemDetailsFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class GatewayErrorResponseWriter {

    public static final String HEADER_NAME = "X-Request-ID";

    private final ObjectMapper objectMapper;

    public GatewayErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, int status, String error, String message) {
        HttpStatus httpStatus = HttpStatus.valueOf(status);
        var response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        var headers = response.getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        // Ensure CORS headers are present on error responses when Origin is set (for browser clients).
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (StringUtils.hasText(origin)) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        headers.set(HEADER_NAME, correlationId);

        ProblemDetail body = ApiProblemDetailsFactory.problemDetail(
            httpStatus,
            typeFor(httpStatus),
            titleFor(httpStatus, error),
            message,
            resolveInstancePath(exchange)
        );

        byte[] payload = toJsonBytes(body);

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(payload);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public static String resolveCorrelationId(String headerValue) {
        if (StringUtils.hasText(headerValue)) {
            return headerValue.trim();
        }
        return UUID.randomUUID().toString();
    }

    private byte[] toJsonBytes(Object body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return ("{\"type\":\"https://api.example.com/errors/internal-server-error\",\"title\":\"Internal server error\",\"status\":500,\"detail\":\"Internal server error\"}")
                .getBytes(StandardCharsets.UTF_8);
        }
    }

    private String typeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "invalid-request";
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "access-denied";
            case NOT_FOUND -> "resource-not-found";
            case METHOD_NOT_ALLOWED -> "method-not-allowed";
            case TOO_MANY_REQUESTS -> "rate-limit-exceeded";
            case PAYLOAD_TOO_LARGE -> "payload-too-large";
            case SERVICE_UNAVAILABLE -> "external-service-unavailable";
            default -> "internal-server-error";
        };
    }

    private String titleFor(HttpStatus status, String error) {
        if (StringUtils.hasText(error)) {
            return error.trim();
        }
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Access denied";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case TOO_MANY_REQUESTS -> "Rate limit exceeded";
            case PAYLOAD_TOO_LARGE -> "Payload too large";
            case SERVICE_UNAVAILABLE -> "External service unavailable";
            default -> "Internal server error";
        };
    }

    private String resolveInstancePath(ServerWebExchange exchange) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (!StringUtils.hasText(rawPath)) {
            return "/";
        }
        return rawPath;
    }
}