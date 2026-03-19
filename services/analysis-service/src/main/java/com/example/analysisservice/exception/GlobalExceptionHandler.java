package com.example.analysisservice.exception;

import com.example.analysisservice.config.CorrelationIdFilter;
import com.example.analysisservice.web.AiModelOutputException;
import com.example.analysisservice.web.BadRequestException;
import com.example.analysisservice.web.UpstreamServiceException;
import com.example.common.api.ApiProblemDetailsFactory;
import com.example.common.exception.ExternalServiceException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ValidationException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", "Invalid request payload");
    }

    @ExceptionHandler({UpstreamServiceException.class, ExternalServiceException.class})
    public ResponseEntity<ProblemDetail> handleUpstream(RuntimeException ex) {
        log.warn("Upstream dependency failure. correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", "Dependent service temporarily unavailable");
    }

    @ExceptionHandler(AiModelOutputException.class)
    public ResponseEntity<ProblemDetail> handleAiOutput(AiModelOutputException ex) {
        // Deliberately NOT mapped to 503, to avoid circuit breaker cascades on "model said nonsense".
        log.info("AI output invalid. correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "ai-output-invalid", "AI output invalid", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "access-denied", "Access denied", "Access denied");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex) {
        log.error("Unhandled server exception. correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", "Unexpected server error");
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String type, String title, String detail) {
        return ResponseEntity.status(status)
            .body(ApiProblemDetailsFactory.problemDetail(status, type, title, detail, null));
    }
}
