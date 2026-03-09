package com.example.syncservice.controller;

import com.example.syncservice.client.grpc.ProjectConfigGrpcClient;
import com.example.syncservice.dto.SyncAllResultDto;
import com.example.syncservice.dto.SyncRequestDto;
import com.example.syncservice.dto.SyncResultDto;
import com.example.syncservice.service.SyncOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manual REST endpoints for triggering sync jobs on demand.
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class SyncController {

    private final SyncOrchestrator syncOrchestrator;

    @PostMapping("/jira/issues")
    @Operation(summary = "Trigger Jira issue sync for a single project config")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> syncJiraIssues(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Manual Jira sync requested for configId={} by user={}", request.projectConfigId(), userId);

        return syncOrchestrator.syncJiraIssuesAsync(request.projectConfigId())
            .thenApply(result -> buildSuccessResponse(result, HttpStatus.OK))
            .exceptionally(this::buildErrorResponse);
    }

    @PostMapping("/github/commits")
    @Operation(summary = "Trigger GitHub commit sync for a single project config")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> syncGithubCommits(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Manual GitHub sync requested for configId={} by user={}", request.projectConfigId(), userId);

        return syncOrchestrator.syncGithubCommitsAsync(request.projectConfigId())
            .thenApply(result -> buildSuccessResponse(result, HttpStatus.OK))
            .exceptionally(this::buildErrorResponse);
    }

    @PostMapping("/all")
    @Operation(summary = "Trigger Jira and GitHub sync in parallel for a single project config")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> syncAll(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        String correlationId = "API-SYNC-" + UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        log.info("Manual full sync requested for configId={} by user={} correlationId={}",
            request.projectConfigId(), userId, correlationId);

        MDC.put("correlationId", correlationId);
        CompletableFuture<SyncResultDto> jiraFuture;
        CompletableFuture<SyncResultDto> githubFuture;
        try {
            jiraFuture = syncOrchestrator.syncJiraIssuesAsync(request.projectConfigId());
            githubFuture = syncOrchestrator.syncGithubCommitsAsync(request.projectConfigId());
        } finally {
            MDC.remove("correlationId");
        }

        return CompletableFuture.allOf(jiraFuture, githubFuture)
            .thenApply(unused -> {
                SyncResultDto jiraResult = jiraFuture.join();
                SyncResultDto githubResult = githubFuture.join();
                SyncAllResultDto response = SyncAllResultDto.builder()
                    .projectConfigId(request.projectConfigId())
                    .success(jiraResult.isSuccess() && githubResult.isSuccess())
                    .degraded(jiraResult.isDegraded() || githubResult.isDegraded())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .correlationId(correlationId)
                    .jira(jiraResult)
                    .github(githubResult)
                    .build();

                return buildSuccessResponse(response, HttpStatus.OK);
            })
            .exceptionally(this::buildErrorResponse);
    }

    private ResponseEntity<Map<String, Object>> buildSuccessResponse(Object data, HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
            "data", data,
            "timestamp", Instant.now().toString()
        ));
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "SYNC_EXECUTION_FAILED";
        String message = cause.getMessage() == null ? "Unexpected sync error" : cause.getMessage();

        if (cause instanceof ProjectConfigGrpcClient.ConfigNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            code = "CONFIG_NOT_FOUND";
        } else if (cause instanceof ProjectConfigGrpcClient.GrpcClientException) {
            status = HttpStatus.BAD_GATEWAY;
            code = "PROJECT_CONFIG_UNAVAILABLE";
        } else if (cause instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            code = "BAD_REQUEST";
        }

        return ResponseEntity.status(status).body(Map.of(
            "error", Map.of(
                "code", code,
                "message", message
            ),
            "timestamp", Instant.now().toString()
        ));
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
            && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return parseUserId(jwt.getSubject());
        }

        if (principal instanceof String value) {
            return parseUserId(value);
        }

        return parseUserId(authentication.getName());
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("Missing authenticated user identifier");
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid authenticated user identifier", ex);
        }
    }
}