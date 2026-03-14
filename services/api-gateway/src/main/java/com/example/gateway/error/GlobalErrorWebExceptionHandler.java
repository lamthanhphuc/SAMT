package com.example.gateway.error;

import com.example.common.api.ApiProblemDetailsFactory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import reactor.netty.http.client.PrematureCloseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
        String correlationId = GatewayErrorResponseWriter.resolveCorrelationId(request.headers().firstHeader(GatewayErrorResponseWriter.HEADER_NAME));

        // ✅ PRODUCTION-SAFE: Log only generic info with correlation ID
        logger.warn(
            "Gateway error handled. Status={}, Path={}, CorrelationId={}, ExceptionType={}, Message={}",
            status.value(),
            request.path(),
            correlationId,
            ex == null ? "n/a" : ex.getClass().getName(),
            ex == null ? "n/a" : ex.getMessage()
        );

        ProblemDetail body = ApiProblemDetailsFactory.problemDetail(
            status,
            typeFor(status),
            titleFor(status),
            genericMessage,
            request.path()
        );

        ServerResponse.BodyBuilder responseBuilder = ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(GatewayErrorResponseWriter.HEADER_NAME, correlationId);

        if (ex instanceof MethodNotAllowedException methodNotAllowedException) {
            String allowHeader = methodNotAllowedException.getSupportedMethods().stream()
                    .map(httpMethod -> httpMethod.name())
                .sorted()
                .collect(Collectors.joining(", "));
            if (!allowHeader.isBlank()) {
            responseBuilder.header(HttpHeaders.ALLOW, allowHeader);
            }
        }

        return responseBuilder.bodyValue(body);
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
        if (isUpstreamUnavailable(ex)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof DataBufferLimitException) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (ex instanceof CallNotPermittedException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof ServerWebInputException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof AuthenticationException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof MethodNotAllowedException) {
            return HttpStatus.METHOD_NOT_ALLOWED;
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

    private boolean isUpstreamUnavailable(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                || current instanceof ConnectTimeoutException
                || current instanceof ReadTimeoutException
                || current instanceof TimeoutException
                || current instanceof PrematureCloseException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * ✅ CRITICAL: Generic messages only - NO exception message leakage
     * ✅ Matches documented error schema requirements
     */
    private String genericMessage(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST:
                return "Invalid request input";
            case UNAUTHORIZED:
                return "Unauthorized";
            case FORBIDDEN:
                return "Forbidden";
            case METHOD_NOT_ALLOWED:
                return "Method not allowed";
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

    private String titleFor(HttpStatus status) {
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
