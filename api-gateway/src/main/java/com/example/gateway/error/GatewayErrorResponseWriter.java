package com.example.gateway.error;

import com.example.common.api.ApiProblemDetailsFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
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
        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        ProblemDetail body = ApiProblemDetailsFactory.problemDetail(
            httpStatus,
            typeFor(httpStatus),
            titleFor(httpStatus, error),
            message,
            exchange.getRequest().getURI().getPath()
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
}