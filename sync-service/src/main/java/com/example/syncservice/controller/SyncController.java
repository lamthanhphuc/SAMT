package com.example.syncservice.controller;

import com.example.common.api.ApiResponse;
import com.example.common.api.ApiResponseFactory;
import com.example.syncservice.dto.SyncAllResultDto;
import com.example.syncservice.dto.SyncRequestDto;
import com.example.syncservice.dto.SyncResultDto;
import com.example.syncservice.service.SyncOrchestrator;
import com.example.syncservice.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Jira sync completed",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Configuration not found",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    public CompletableFuture<ResponseEntity<ApiResponse<SyncResultDto>>> syncJiraIssues(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Manual Jira sync requested for configId={} by user={}", request.projectConfigId(), userId);

        return syncOrchestrator.syncJiraIssuesAsync(request.projectConfigId())
            .thenApply(result -> buildSuccessResponse(result, servletRequest, result.isDegraded()));
    }

    @PostMapping("/github/commits")
    @Operation(summary = "Trigger GitHub commit sync for a single project config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "GitHub sync completed",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Configuration not found",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    public CompletableFuture<ResponseEntity<ApiResponse<SyncResultDto>>> syncGithubCommits(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        log.info("Manual GitHub sync requested for configId={} by user={}", request.projectConfigId(), userId);

        return syncOrchestrator.syncGithubCommitsAsync(request.projectConfigId())
            .thenApply(result -> buildSuccessResponse(result, servletRequest, result.isDegraded()));
    }

    @PostMapping("/all")
    @Operation(summary = "Trigger Jira and GitHub sync in parallel for a single project config")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All sync jobs completed",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Configuration not found",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "At least one downstream sync failed",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    public CompletableFuture<ResponseEntity<ApiResponse<SyncAllResultDto>>> syncAll(
        @Valid @RequestBody SyncRequestDto request,
        Authentication authentication,
        HttpServletRequest servletRequest
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        String correlationId = resolveCorrelationId(servletRequest);
        long startTime = System.currentTimeMillis();

        log.info("Manual full sync requested for configId={} by user={} correlationId={}",
            request.projectConfigId(), userId, correlationId);

        CompletableFuture<SyncBranchOutcome> jiraFuture = syncOrchestrator.syncJiraIssuesAsync(request.projectConfigId())
            .handle((result, throwable) -> SyncBranchOutcome.from(
                SyncResultDto.builder()
                    .projectConfigId(request.projectConfigId())
                    .jobType("JIRA_ISSUES")
                    .correlationId(correlationId)
                    .build(),
                result,
                throwable
            ));
        CompletableFuture<SyncBranchOutcome> githubFuture = syncOrchestrator.syncGithubCommitsAsync(request.projectConfigId())
            .handle((result, throwable) -> SyncBranchOutcome.from(
                SyncResultDto.builder()
                    .projectConfigId(request.projectConfigId())
                    .jobType("GITHUB_COMMITS")
                    .correlationId(correlationId)
                    .build(),
                result,
                throwable
            ));

        return jiraFuture.thenCombine(githubFuture, (jiraOutcome, githubOutcome) -> {
                if (jiraOutcome.throwable() != null) {
                    return buildSyncUnavailableResponse(
                        servletRequest,
                        correlationId,
                        jiraOutcome.throwable()
                    );
                }
                if (githubOutcome.throwable() != null) {
                    return buildSyncUnavailableResponse(
                        servletRequest,
                        correlationId,
                        githubOutcome.throwable()
                    );
                }

                SyncResultDto jiraResult = jiraOutcome.toResult();
                SyncResultDto githubResult = githubOutcome.toResult();
                boolean degraded = jiraResult.isDegraded()
                    || githubResult.isDegraded();

                SyncAllResultDto response = SyncAllResultDto.builder()
                    .projectConfigId(request.projectConfigId())
                    .success(true)
                    .degraded(degraded)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .correlationId(correlationId)
                    .jira(jiraResult)
                    .github(githubResult)
                    .build();

                return ResponseEntity.ok(
                    ApiResponseFactory.success(
                        HttpStatus.OK.value(),
                        response,
                        servletRequest.getRequestURI(),
                        correlationId,
                        degraded
                    )
                );
            })
            .exceptionally(throwable -> buildSyncUnavailableResponse(servletRequest, correlationId, throwable))
            ;
    }

    private ResponseEntity<ApiResponse<SyncAllResultDto>> buildSyncUnavailableResponse(
        HttpServletRequest request,
        String correlationId,
        Throwable throwable
    ) {
        String message = throwable == null ? "Sync service is temporarily unavailable" : throwable.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiResponse<>(
                java.time.OffsetDateTime.now().toString(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                false,
                null,
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                message,
                request.getRequestURI(),
                correlationId,
                null
            ));
    }

    private ResponseEntity<ApiResponse<SyncResultDto>> buildSuccessResponse(
        SyncResultDto data,
        HttpServletRequest request,
        boolean degraded
    ) {
        return ResponseEntity.ok(
            ApiResponseFactory.success(
                HttpStatus.OK.value(),
                data,
                request.getRequestURI(),
                resolveCorrelationId(request),
                degraded ? Boolean.TRUE : null
            )
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
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

    private record SyncBranchOutcome(SyncResultDto fallbackResult, Throwable throwable) {
        private static SyncBranchOutcome from(SyncResultDto baseResult, SyncResultDto result, Throwable throwable) {
            Throwable cause = throwable;
            while (cause != null
                && (cause instanceof java.util.concurrent.CompletionException
                    || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
                cause = cause.getCause();
            }

            if (cause == null) {
                return new SyncBranchOutcome(result, null);
            }

            SyncResultDto failureResult = SyncResultDto.builder()
                .syncJobId(baseResult.getSyncJobId())
                .projectConfigId(baseResult.getProjectConfigId())
                .jobType(baseResult.getJobType())
                .success(false)
                .degraded(false)
                .recordsFetched(0)
                .recordsSaved(0)
                .durationMs(0)
                .errorMessage(cause.getMessage())
                .correlationId(baseResult.getCorrelationId())
                .build();
            return new SyncBranchOutcome(failureResult, cause);
        }

        private SyncResultDto toResult() {
            return fallbackResult;
        }
    }
}