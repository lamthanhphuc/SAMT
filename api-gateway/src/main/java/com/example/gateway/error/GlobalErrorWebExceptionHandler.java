package com.example.gateway.error;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler, Ordered {
    @Override
    public int getOrder() {
        return -2; // Run before default handler
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "INTERNAL_ERROR";
        String message = "Unexpected error";
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            status = rse.getStatusCode();
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            switch (status) {
                case UNAUTHORIZED:
                    code = "UNAUTHORIZED";
                    break;
                case FORBIDDEN:
                    code = "FORBIDDEN";
                    break;
                case NOT_FOUND:
                    code = "NOT_FOUND";
                    break;
                case TOO_MANY_REQUESTS:
                    code = "RATE_LIMIT_EXCEEDED";
                    break;
                case PAYLOAD_TOO_LARGE:
                    code = "PAYLOAD_TOO_LARGE";
                    break;
                case SERVICE_UNAVAILABLE:
                    code = "SERVICE_UNAVAILABLE";
                    break;
                case GATEWAY_TIMEOUT:
                    code = "GATEWAY_TIMEOUT";
                    break;
                default:
                    code = "INTERNAL_ERROR";
                    break;
            }
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String timestamp = ZonedDateTime.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String body = String.format("{\"error\":{\"code\":\"%s\",\"message\":\"%s\"},\"timestamp\":\"%s\"}",
                code, message, timestamp);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
