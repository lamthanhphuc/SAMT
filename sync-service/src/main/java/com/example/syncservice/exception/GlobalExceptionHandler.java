package com.example.syncservice.exception;

import com.example.common.api.ApiProblemDetails;
import com.example.common.api.ApiProblemDetailsFactory;
import com.example.common.exception.ExternalServiceException;
import com.example.syncservice.client.external.GithubClient;
import com.example.syncservice.client.external.JiraClient;
import com.example.syncservice.client.grpc.ProjectConfigGrpcClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler({ConfigNotFoundException.class, GithubClient.RepositoryNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ProjectConfigGrpcClient.NonRetryableGrpcClientException.class)
    public ResponseEntity<ProblemDetail> handleInvalidIntegrationConfig(
        ProjectConfigGrpcClient.NonRetryableGrpcClientException ex,
        HttpServletRequest request
    ) {
        if (ex.isIntegrationConfigurationNotVerified()) {
            return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "integration-not-verified",
                "Integration configuration not verified",
                ex.getDetails(),
                request,
                details -> {
                    details.extension("configState", ex.getConfigState());
                    details.extension("failedServices", ex.getFailedServices());
                }
            );
        }

        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(ProjectConfigGrpcClient.GrpcClientException.class)
    public ResponseEntity<ProblemDetail> handleGrpcClient(ProjectConfigGrpcClient.GrpcClientException ex, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler({
        CallNotPermittedException.class,
        JiraClient.JiraClientException.class,
        GithubClient.GithubClientException.class,
        JiraClient.AuthenticationException.class,
        GithubClient.AuthenticationException.class,
        ExternalServiceException.class
    })
    public ResponseEntity<ProblemDetail> handleExternalService(RuntimeException ex, HttpServletRequest request) {
        String message = ex instanceof CallNotPermittedException
            ? "Project config service is temporarily unavailable"
            : ex.getMessage();
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "external-service-unavailable", "External service unavailable", message, request);
    }

    @ExceptionHandler({
        JiraClient.RateLimitExceededException.class,
        GithubClient.RateLimitExceededException.class
    })
    public ResponseEntity<ProblemDetail> handleRateLimit(RuntimeException ex, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Rate limit exceeded", ex.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class, ConstraintViolationException.class, ValidationException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentNotValidException methodArgumentNotValidException
            ? methodArgumentNotValidException.getBindingResult().getFieldErrors().isEmpty()
                ? "Validation failed"
                : methodArgumentNotValidException.getBindingResult().getFieldErrors().get(0).getDefaultMessage()
            : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", message, request);
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Resource not found", "Resource not found", request);
    }

    @ExceptionHandler({JwtException.class, BadCredentialsException.class})
    public ResponseEntity<ProblemDetail> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "Invalid or expired token", request);
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Access denied", "Forbidden", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "resource-conflict", "Conflict detected", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected sync error: {}", ex.getMessage(), ex);
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