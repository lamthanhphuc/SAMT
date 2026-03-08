package com.samt.projectconfig.exception;

import com.samt.projectconfig.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Converts exceptions to consistent ErrorResponse format.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConfigNotFound(ConfigNotFoundException ex) {
        log.warn("Config not found: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "CONFIG_NOT_FOUND",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(GroupNotFoundException ex) {
        log.warn("Group not found: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "GROUP_NOT_FOUND",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(ConfigAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConfigAlreadyExists(ConfigAlreadyExistsException ex) {
        log.warn("Config already exists: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            "CONFIG_ALREADY_EXISTS",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            "CONFLICT",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler({
        jakarta.persistence.OptimisticLockException.class,
        org.hibernate.StaleObjectStateException.class,
        org.springframework.orm.ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            "CONFLICT",
            "Configuration was modified by another user. Please refresh and retry.",
            null
        );
    }
    
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
        org.springframework.dao.DataIntegrityViolationException ex
    ) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        
        // Check if it's a unique constraint violation
        String message = ex.getMessage();
        if (message != null && message.toLowerCase().contains("unique")) {
            return buildErrorResponse(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "A configuration for this group already exists",
                null
            );
        }
        
        // Generic data integrity error
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "DATA_INTEGRITY_ERROR",
            "Data integrity constraint violated",
            null
        );
    }
    
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Service unavailable: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICE_UNAVAILABLE",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(EncryptionException.class)
    public ResponseEntity<ErrorResponse> handleEncryption(EncryptionException ex) {
        log.error("Encryption error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "Encryption error occurred",
            null
        );
    }
    
    @ExceptionHandler(VerificationException.class)
    public ResponseEntity<ErrorResponse> handleVerification(VerificationException ex) {
        log.warn("Verification error: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.GATEWAY_TIMEOUT,
            "GATEWAY_TIMEOUT",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(java.util.concurrent.RejectedExecutionException.class)
    public ResponseEntity<ErrorResponse> handleRejectedExecution(java.util.concurrent.RejectedExecutionException ex) {
        log.warn("Verification executor overloaded - rejected task");
        return buildErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICE_OVERLOADED",
            "Verification service is temporarily overloaded. Please retry later.",
            null
        );
    }
    
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleGatewayTimeout(GatewayTimeoutException ex) {
        log.error("Gateway timeout: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.GATEWAY_TIMEOUT,
            "GATEWAY_TIMEOUT",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwt(JwtException ex) {
        log.warn("JWT error: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "Invalid or expired token",
            null
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "Invalid or expired token",
            null
        );
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        String firstField = ex.getBindingResult().getFieldErrors().isEmpty() 
            ? null 
            : ex.getBindingResult().getFieldErrors().get(0).getField();
        String firstMessage = ex.getBindingResult().getFieldErrors().isEmpty()
            ? "Validation failed"
            : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            firstMessage,
            firstField,
            errors
        );
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Validation failed",
            null
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        if (java.util.UUID.class.equals(ex.getRequiredType())) {
            message = "Invalid UUID";
        }
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            ex.getName()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Missing required parameter '" + ex.getParameterName() + "'",
            ex.getParameterName()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Malformed request body",
            null
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header(HttpHeaders.ALLOW, resolveAllowHeader(ex))
            .body(buildErrorBody(
            HttpStatus.METHOD_NOT_ALLOWED,
            HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
            "Method not allowed",
            null
        ));
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildErrorResponse(
            HttpStatus.CONFLICT,
            "CONFLICT",
            ex.getMessage(),
            null
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            null
        );
    }
    
    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String code,
        String message,
        String field
    ) {
        return buildErrorResponse(status, code, message, field, null);
    }
    
    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String code,
        String message,
        String field,
        Object details
    ) {
        return ResponseEntity.status(status).body(buildErrorBody(status, code, message, details != null ? details : field));
    }

    private ErrorResponse buildErrorBody(
        HttpStatus status,
        String code,
        String message,
        Object details
    ) {
        ErrorResponse response = ErrorResponse.builder()
            .statusCode(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .details(details)
            .timestamp(Instant.now().toString())
            .build();

        return response;
    }

    private String resolveAllowHeader(HttpRequestMethodNotSupportedException ex) {
        String[] supportedMethods = ex.getSupportedMethods();
        if (supportedMethods == null || supportedMethods.length == 0) {
            return "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD";
        }
        return String.join(", ", Arrays.stream(supportedMethods).sorted().toList());
    }
}
