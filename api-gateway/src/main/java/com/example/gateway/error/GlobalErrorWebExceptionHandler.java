package com.example.gateway.error;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Error Handler for API Gateway
 * ✅ NEVER returns null 
 * ✅ NO fallback to default Spring handler
 * ✅ NO exception message leakage
 * ✅ Always returns documented JSON schema
 * ✅ Does NOT transform downstream 4xx/5xx responses
 */
@Component
@Order(-1)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    public GlobalErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer
    ) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        setMessageWriters(serverCodecConfigurer.getWriters());
        setMessageReaders(serverCodecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    /**
     * ✅ CRITICAL: This method NEVER returns null
     * ✅ CRITICAL: Always returns ServerResponse with JSON schema
     * ✅ CRITICAL: No exception message leakage
     */
    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable ex = getError(request);
        HttpStatus status = resolveStatus(ex);
        String genericMessage = genericMessage(status);
        String errorId = UUID.randomUUID().toString().substring(0, 8);

        // ✅ PRODUCTION-SAFE: Log only generic info with correlation ID
        logger.warn("Gateway error handled. ErrorId={}, Status={}, Path={}", 
                   errorId, status.value(), request.path());

        // Create error object according to documented schema
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", status.value());
        error.put("message", genericMessage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("timestamp", Instant.now().toString());

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
    }

    @Override
    protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
        // ✅ PRODUCTION-SAFE: No logging here - already handled in renderErrorResponse
        // ✅ CRITICAL: NEVER log exception details, messages, or class names
    }

    /**
     * ✅ Resolve HTTP status from exception
     * ✅ Handle all gateway-level exceptions  
     * ✅ Default to 500 for unmapped exceptions (NO null return)
     */
    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof DataBufferLimitException) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (ex instanceof CallNotPermittedException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof ServerWebInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException responseStatusException = (ResponseStatusException) ex;
            HttpStatus resolved = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (resolved != null) {
                return resolved;
            }
        }
        // ✅ CRITICAL: Default case - NEVER return null
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * ✅ CRITICAL: Generic messages only - NO exception message leakage
     * ✅ Matches documented error schema requirements
     */
    private String genericMessage(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST:
                return "Bad request";
            case UNAUTHORIZED:
                return "Unauthorized";
            case FORBIDDEN:
                return "Forbidden";
            case NOT_FOUND:
                return "Not found";
            case PAYLOAD_TOO_LARGE:
                return "Request size exceeds 10MB";
            case TOO_MANY_REQUESTS:
                return "Too many requests";
            case SERVICE_UNAVAILABLE:
                return "Service unavailable";
            default:
                return "Internal server error";
        }
    }
}
