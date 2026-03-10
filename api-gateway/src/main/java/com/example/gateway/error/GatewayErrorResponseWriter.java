package com.example.gateway.error;

import com.example.common.api.ApiResponseFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        byte[] payload = toJsonBytes(
            ApiResponseFactory.error(
                status,
                error,
                message,
                exchange.getRequest().getURI().getPath(),
                correlationId
            )
        );

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
            return ("{\"status\":500,\"success\":false,\"error\":\"Internal Server Error\",\"message\":\"Internal server error\"}")
                .getBytes(StandardCharsets.UTF_8);
        }
    }
}