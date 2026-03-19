package com.example.reportservice.web;

import com.example.common.api.ApiProblemDetailsFactory;
import com.example.common.exception.ExternalServiceException;
import com.example.reportservice.config.CorrelationIdFilter;
import com.example.reportservice.dto.response.ReportGenerationFailureResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ReportGenerationFailedException.class)
    public ResponseEntity<ReportGenerationFailureResponse> handleReportGenerationFailed(ReportGenerationFailedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ReportGenerationFailureResponse(
                        "FAILED",
                        ex.getStep(),
                        ex.getReason(),
                        ex.getLogs()
                ));
    }

    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage(), null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class, ConstraintViolationException.class, ValidationException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", "Invalid request payload", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Malformed request body", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String parameterName = ex.getName() == null ? "parameter" : ex.getName();
        return build(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Invalid value for parameter '" + parameterName + "'", request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", "Missing required parameter '" + ex.getParameterName() + "'", request.getRequestURI());
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", "Resource not found", request.getRequestURI());
    }

    @ExceptionHandler({UpstreamServiceException.class, ExternalServiceException.class})
    public ResponseEntity<ProblemDetail> handleUpstream(RuntimeException ex) {
        log.warn("Upstream dependency failure. correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", "Dependent service temporarily unavailable", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "access-denied", "Access denied", "Access denied", null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception. path={} correlationId={}", request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal server error", "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String type, String title, String detail, String instance) {
        return ResponseEntity.status(status)
            .body(ApiProblemDetailsFactory.problemDetail(status, type, title, detail, instance));
    }

    private String resolveAllowHeader(HttpRequestMethodNotSupportedException ex) {
        String[] supportedMethods = ex.getSupportedMethods();
        if (supportedMethods == null || supportedMethods.length == 0) {
            return "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD";
        }
        return String.join(", ", Arrays.stream(supportedMethods).sorted().toList());
    }
}
