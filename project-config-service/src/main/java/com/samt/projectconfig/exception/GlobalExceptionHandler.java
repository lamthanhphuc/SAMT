package com.samt.projectconfig.exception;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
        log.warn("Config not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleGroupNotFound(GroupNotFoundException ex, HttpServletRequest request) {
        log.warn("Group not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("Forbidden: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ConfigAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleConfigAlreadyExists(ConfigAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Config already exists: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({
        jakarta.persistence.OptimisticLockException.class,
        org.hibernate.StaleObjectStateException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Configuration was modified by another user. Please refresh and retry.", request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
        org.springframework.dao.DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String text = ex.getMessage();
        if (text != null && text.toLowerCase().contains("unique")) {
            return error(HttpStatus.CONFLICT, "A configuration for this group already exists", request);
        }
        return error(HttpStatus.BAD_REQUEST, "Data integrity constraint violated", request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Service unavailable: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleEncryption(EncryptionException ex, HttpServletRequest request) {
        log.error("Encryption error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Encryption error occurred", request);
    }

    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerification(VerificationException ex, HttpServletRequest request) {
        log.warn("Verification error: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(java.util.concurrent.RejectedExecutionException.class)
    public ResponseEntity<ApiResponse<Void>> handleRejectedExecution(java.util.concurrent.RejectedExecutionException ex, HttpServletRequest request) {
        log.warn("Verification executor overloaded - rejected task");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Verification service is temporarily overloaded. Please retry later.", request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayTimeout(GatewayTimeoutException ex, HttpServletRequest request) {
        log.error("Gateway timeout: {}", ex.getMessage());
        return error(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage(), request);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleJwt(JwtException ex, HttpServletRequest request) {
        log.warn("JWT error: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
            ? "Validation failed"
            : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = java.util.UUID.class.equals(ex.getRequiredType())
            ? "Invalid UUID"
            : "Invalid value for parameter '" + ex.getName() + "'";
        return error(HttpStatus.BAD_REQUEST, message, request);
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
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
        String correlationId = request.getHeader("X-Request-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("correlationId");
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