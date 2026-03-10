package com.example.syncservice.exception;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiResponseFactory;
import com.example.syncservice.client.external.GithubClient;
import com.example.syncservice.client.external.JiraClient;
import com.example.syncservice.client.grpc.ProjectConfigGrpcClient;
import com.example.syncservice.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ResponseEntity<ApiResponse<Void>> handleAsyncWrapper(Exception ex, HttpServletRequest request) {
        Throwable cause = unwrap(ex);
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return handleGeneric(ex, request);
    }

    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleConfigNotFound(ConfigNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ProjectConfigGrpcClient.GrpcClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleGrpcClient(ProjectConfigGrpcClient.GrpcClientException ex, HttpServletRequest request) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout")
            ? HttpStatus.GATEWAY_TIMEOUT
            : HttpStatus.SERVICE_UNAVAILABLE;
        return error(status, ex.getMessage(), request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCircuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Project config service is temporarily unavailable", request);
    }

    @ExceptionHandler({
        JiraClient.JiraClientException.class,
        GithubClient.GithubClientException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleExternalService(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler({
        JiraClient.RateLimitExceededException.class,
        GithubClient.RateLimitExceededException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler({
        JiraClient.AuthenticationException.class,
        GithubClient.AuthenticationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleExternalAuthentication(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(GithubClient.RepositoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRepositoryNotFound(GithubClient.RepositoryNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
            ? "Validation failed"
            : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header(HttpHeaders.ALLOW, resolveAllowHeader(ex))
            .body(errorBody(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", request));
    }

    @ExceptionHandler({JwtException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected sync error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(errorBody(status, message, request));
    }

    private ApiResponse<Void> errorBody(HttpStatus status, String message, HttpServletRequest request) {
        return ApiResponseFactory.error(
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            resolveCorrelationId(request)
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String resolveAllowHeader(HttpRequestMethodNotSupportedException ex) {
        String[] supportedMethods = ex.getSupportedMethods();
        if (supportedMethods == null || supportedMethods.length == 0) {
            return "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD";
        }
        return String.join(", ", Arrays.stream(supportedMethods).sorted().toList());
    }
}