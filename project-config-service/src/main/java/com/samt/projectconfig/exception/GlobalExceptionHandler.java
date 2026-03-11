package com.samt.projectconfig.exception;

import com.example.common.api.ApiProblemDetails;
import com.example.common.api.ApiProblemDetailsFactory;
import com.example.common.exception.ExternalServiceException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.function.Consumer;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ResponseEntity<ProblemDetail> handleAsyncWrapper(Exception ex, HttpServletRequest request) {
        Throwable cause = unwrap(ex);
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        return handleGeneric(ex, request);
    }

    @ExceptionHandler({ConfigNotFoundException.class, GroupNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ProblemDetail> handleForbidden(RuntimeException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied", ex.getMessage(), request);
    }

    @ExceptionHandler({
        ConfigAlreadyExistsException.class,
        ConflictException.class,
        IllegalStateException.class,
        jakarta.persistence.OptimisticLockException.class,
        org.hibernate.StaleObjectStateException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(Exception ex, HttpServletRequest request) {
        String message = (ex instanceof jakarta.persistence.OptimisticLockException
            || ex instanceof org.hibernate.StaleObjectStateException
            || ex instanceof org.springframework.orm.ObjectOptimisticLockingFailureException)
            ? "Configuration was modified by another user. Please refresh and retry."
            : ex.getMessage();
        log.warn("Conflict: {}", message);
        return problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", message, request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
        org.springframework.dao.DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        String text = ex.getMessage();
        if (text != null && text.toLowerCase().contains("unique")) {
            return problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", "A configuration for this group already exists", request);
        }
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Data integrity constraint violated", request);
    }

    @ExceptionHandler({
        ServiceUnavailableException.class,
        VerificationException.class,
        java.util.concurrent.RejectedExecutionException.class,
        ExternalServiceException.class
    })
    public ResponseEntity<ProblemDetail> handleServiceUnavailable(RuntimeException ex, HttpServletRequest request) {
        String detail = ex instanceof java.util.concurrent.RejectedExecutionException
            ? "Verification service is temporarily overloaded. Please retry later."
            : ex.getMessage();
        log.warn("Service unavailable: {}", detail);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", detail, request);
    }

    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ProblemDetail> handleEncryption(EncryptionException ex, HttpServletRequest request) {
        log.error("Encryption error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", "Encryption error occurred", request);
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleGatewayTimeout(GatewayTimeoutException ex, HttpServletRequest request) {
        log.error("Gateway timeout: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler({JwtException.class, BadCredentialsException.class})
    public ResponseEntity<ProblemDetail> handleUnauthorized(Exception ex, HttpServletRequest request) {
        log.warn("Unauthorized request: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "Invalid or expired token", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, ValidationException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        String message = ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
            ? methodArgumentNotValidException.getBindingResult().getFieldErrors().isEmpty()
                ? "Validation failed"
                : methodArgumentNotValidException.getBindingResult().getFieldErrors().get(0).getDefaultMessage()
            : "Validation failed";
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = java.util.UUID.class.equals(ex.getRequiredType())
            ? "Invalid UUID"
            : "Invalid value for parameter '" + ex.getName() + "'";
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Missing required parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Malformed request body", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header(HttpHeaders.ALLOW, resolveAllowHeader(ex))
            .body(ApiProblemDetailsFactory.problemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "method-not-allowed",
                "Method not allowed",
                "Method not allowed",
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", "An unexpected error occurred", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        return problem(status, type, title, detail, request, null);
    }

    private ResponseEntity<ProblemDetail> problem(
        HttpStatus status,
        String type,
        String title,
        String detail,
        HttpServletRequest request,
        Consumer<ApiProblemDetails> customizer
    ) {
        return ResponseEntity.status(status)
            .body(ApiProblemDetailsFactory.problemDetail(status, type, title, detail, request.getRequestURI(), customizer));
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