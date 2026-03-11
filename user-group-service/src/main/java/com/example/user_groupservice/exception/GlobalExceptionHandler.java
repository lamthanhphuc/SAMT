package com.example.user_groupservice.exception;

import com.example.common.api.ApiProblemDetailsFactory;
import com.example.common.exception.ExternalServiceException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ProblemDetail> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.warn("Business exception: {} - {}", ex.getCode(), ex.getMessage());
        return switch (ex.getStatus()) {
            case BAD_REQUEST -> problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage(), request);
            case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied", ex.getMessage(), request);
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage(), request);
            case CONFLICT -> problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", ex.getMessage(), request);
            case SERVICE_UNAVAILABLE -> problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", ex.getMessage(), request);
            default -> problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", ex.getMessage(), request);
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, ValidationException.class})
    public ResponseEntity<ProblemDetail> handleValidationException(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
            ? methodArgumentNotValidException.getBindingResult().getFieldErrors().isEmpty()
                ? "Validation failed"
                : methodArgumentNotValidException.getBindingResult().getFieldErrors().get(0).getDefaultMessage()
            : "Validation failed";
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", message, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "Authentication failed", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied", "You do not have permission to perform this action", request);
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class, IllegalStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ProblemDetail> handleConflict(Exception ex, HttpServletRequest request) {
        String message = ex instanceof DataIntegrityViolationException
            ? "Request conflicts with existing or missing related data"
            : ex instanceof IllegalStateException
                ? ex.getMessage()
                : "Resource has been modified by another user. Please refresh and retry.";
        return problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", message, request);
    }

    @ExceptionHandler({EntityNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ProblemDetail> handleExternalService(ExternalServiceException ex, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Invalid value for parameter '" + ex.getName() + "'", request);
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
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", "An unexpected error occurred", request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiProblemDetailsFactory.problemDetail(status, type, title, detail, request.getRequestURI()));
    }

    private String resolveAllowHeader(HttpRequestMethodNotSupportedException ex) {
        String[] supportedMethods = ex.getSupportedMethods();
        if (supportedMethods == null || supportedMethods.length == 0) {
            return "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD";
        }
        return String.join(", ", Arrays.stream(supportedMethods).sorted().toList());
    }
}