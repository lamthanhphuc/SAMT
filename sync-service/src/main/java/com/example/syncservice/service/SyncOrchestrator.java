package com.example.syncservice.service;

import com.example.syncservice.client.external.GithubClient;
import com.example.syncservice.client.external.JiraClient;
import com.example.syncservice.client.grpc.ProjectConfigGrpcClient;
import com.example.syncservice.dto.GithubCommitDto;
import com.example.syncservice.dto.JiraIssueDto;
import com.example.syncservice.dto.ProjectConfigDto;
import com.example.syncservice.dto.SyncResultDto;
import com.example.syncservice.entity.*;
import com.example.syncservice.metrics.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrator for sync operations.
 * 
 * CRITICAL DESIGN:
 * - External API calls OUTSIDE transactions
 * - Database writes INSIDE transactions (via SyncDataService)
 * - Async execution using bounded thread pool
 * - Correlation ID propagation for tracing
 * - Metrics tracking for observability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncOrchestrator {

    private final ProjectConfigGrpcClient projectConfigGrpcClient;
    private final JiraClient jiraClient;
    private final GithubClient githubClient;
    private final SyncDataService syncDataService;
    private final DataMapper dataMapper;
    private final SyncMetrics syncMetrics;
    private final FallbackSignal fallbackSignal;

    /**
     * Execute sync for all verified project configs.
     * Called by scheduler.
     */
    public void executeFullSync() {
        String correlationId = "SYNC-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            log.info("Starting full sync: correlationId={}", correlationId);

            // Fetch verified config IDs via gRPC (OUTSIDE transaction)
            List<Long> configIds = projectConfigGrpcClient.listVerifiedConfigIds();
            log.info("Found {} verified configs to sync", configIds.size());

            if (configIds.isEmpty()) {
                log.info("No verified configs found, skipping sync");
                return;
            }

            // Submit async jobs for each config
            List<CompletableFuture<SyncResultDto>> futures = new ArrayList<>();
            int rejectionCount = 0;
            for (Long configId : configIds) {
                try {
                    futures.add(syncJiraIssuesAsync(configId));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    // AbortPolicy: Queue full, task rejected
                    // Catches both RejectedExecutionException and its subclass TaskRejectedException
                    log.error("❌ CRITICAL: Sync queue full, task rejected for configId={}. Exception type: {}. Increase thread pool capacity.", 
                              configId, e.getClass().getSimpleName());
                    syncMetrics.recordSyncRejection();
                    rejectionCount++;
                    // Continue with remaining configs (fail-fast per config, not entire batch)
                }
            }

            // Record batch-level partial rejection if at least 1 task rejected
            if (rejectionCount > 0) {
                syncMetrics.recordSyncBatchPartialRejection(rejectionCount, configIds.size());
            }

            // Wait for all to complete (with timeout handled by async executor)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> log.info("All sync jobs completed"))
                    .exceptionally(ex -> {
                        log.error("Error waiting for sync jobs: {}", ex.getMessage());
                        return null;
                    })
                    .join();

        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Sync Jira issues for a single project config (async).
     * 
     * FLOW:
     * 1. Create sync job (transaction)
     * 2. Fetch config via gRPC (OUTSIDE transaction)
     * 3. Fetch issues from Jira API (OUTSIDE transaction)
     * 4. Transform data (in-memory)
     * 5. Persist to DB (transaction via SyncDataService)
     * 6. Update sync job status (transaction)
     */
    @Async("syncTaskExecutor")
    public CompletableFuture<SyncResultDto> syncJiraIssuesAsync(Long projectConfigId) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = "JIRA-" + UUID.randomUUID().toString().substring(0, 8);
            MDC.put("correlationId", correlationId);
        }

        SyncJob syncJob = null;
        try (FallbackSignal signal = fallbackSignal.acquire()) {
            try {
                log.info("Starting Jira sync for configId={}", projectConfigId);

            // Record job started for failure rate tracking
            syncMetrics.recordSyncJobStarted();

            // Step 1: Create sync job (SHORT transaction)
            syncJob = syncDataService.createSyncJob(projectConfigId, SyncJob.JobType.JIRA_ISSUES, correlationId);

            // Step 2: Fetch config via gRPC (OUTSIDE transaction)
            ProjectConfigDto config = projectConfigGrpcClient.getDecryptedConfig(projectConfigId);
            log.debug("Fetched config for configId={}: jiraHost={}", projectConfigId, config.getJiraHostUrl());

            // Step 3: Fetch issues from Jira API (OUTSIDE transaction)
            // CRITICAL: Fallback may be triggered here, setting degraded flag
            List<JiraIssueDto> issueDtos = jiraClient.fetchIssues(
                    config.getJiraHostUrl(),
                    config.getJiraApiToken(),
                    config.getJiraProjectKey(),
                    100);

            // Check if fallback was triggered (degraded execution)
            boolean degraded = fallbackSignal.isDegraded();
            String degradationReason = fallbackSignal.getReason();

            log.info("Fetched {} Jira issues for configId={}, degraded={}", 
                    issueDtos.size(), projectConfigId, degraded);

            // Step 4: Transform data (in-memory, NO transaction)
            List<UnifiedActivity> unifiedActivities = issueDtos.stream()
                    .map(dto -> dataMapper.jiraIssueToUnifiedActivity(dto, projectConfigId))
                    .collect(Collectors.toList());

            List<JiraIssue> jiraIssues = issueDtos.stream()
                    .map(dto -> dataMapper.jiraIssueDtoToEntity(dto, projectConfigId))
                    .collect(Collectors.toList());

            // Step 5: Persist to DB (SHORT transactions)
            int savedActivities = syncDataService.persistUnifiedActivities(unifiedActivities);
            syncDataService.persistJiraIssues(jiraIssues);

            // Step 6: Update sync job status based on execution result
            long duration = System.currentTimeMillis() - startTime;
            
            if (degraded) {
                // Fallback triggered → PARTIAL_FAILURE
                syncDataService.markSyncJobAsPartialFailure(
                        syncJob.getId(), 
                        issueDtos.size(), 
                        savedActivities, 
                        degradationReason);
                syncMetrics.recordSyncPartialFailure(SyncJob.JobType.JIRA_ISSUES, duration);
                
                log.warn("⚠️ Jira sync PARTIAL_FAILURE for configId={}: reason={}", 
                        projectConfigId, degradationReason);
            } else {
                // Normal execution → COMPLETED (even if 0 records)
                syncDataService.completeSyncJob(syncJob.getId(), issueDtos.size(), savedActivities);
                syncMetrics.recordSyncSuccess(SyncJob.JobType.JIRA_ISSUES, duration);
                
                log.info("✅ Completed Jira sync for configId={}: fetched={}, saved={}, duration={}ms",
                        projectConfigId, issueDtos.size(), savedActivities, duration);
            }

                return CompletableFuture.completedFuture(SyncResultDto.builder()
                        .syncJobId(syncJob.getId())
                        .projectConfigId(projectConfigId)
                        .jobType(SyncJob.JobType.JIRA_ISSUES.name())
                        .success(!degraded)
                        .degraded(degraded)
                        .recordsFetched(issueDtos.size())
                        .recordsSaved(savedActivities)
                        .durationMs(duration)
                        .errorMessage(degraded ? degradationReason : null)
                        .correlationId(correlationId)
                        .build());

            } catch (Exception e) {
                log.error("❌ Failed Jira sync for configId={}: {}", projectConfigId, e.getMessage(), e);

                // Mark sync job as failed (SHORT transaction)
                if (syncJob != null) {
                    syncDataService.failSyncJob(syncJob.getId(), e.getMessage());
                }

                syncMetrics.recordSyncFailure(SyncJob.JobType.JIRA_ISSUES);

                return CompletableFuture.completedFuture(SyncResultDto.builder()
                        .syncJobId(syncJob != null ? syncJob.getId() : null)
                        .projectConfigId(projectConfigId)
                        .jobType(SyncJob.JobType.JIRA_ISSUES.name())
                        .success(false)
                        .degraded(false)
                        .errorMessage(e.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .correlationId(correlationId)
                        .build());
            } finally {
                MDC.remove("correlationId");
            }
        }
    }

    /**
     * Sync GitHub commits for a single project config (async).
     * 
     * Similar flow to syncJiraIssuesAsync.
     */
    @Async("syncTaskExecutor")
    public CompletableFuture<SyncResultDto> syncGithubCommitsAsync(Long projectConfigId) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = "GITHUB-" + UUID.randomUUID().toString().substring(0, 8);
            MDC.put("correlationId", correlationId);
        }

        SyncJob syncJob = null;
        try (FallbackSignal signal = fallbackSignal.acquire()) {
            try {
                log.info("Starting GitHub sync for configId={}", projectConfigId);

            // Record job started for failure rate tracking
            syncMetrics.recordSyncJobStarted();

            // Step 1: Create sync job
            syncJob = syncDataService.createSyncJob(projectConfigId, SyncJob.JobType.GITHUB_COMMITS, correlationId);

            // Step 2: Fetch config via gRPC
            ProjectConfigDto config = projectConfigGrpcClient.getDecryptedConfig(projectConfigId);
            log.debug("Fetched config for configId={}: githubRepo={}", projectConfigId, config.getGithubRepoUrl());

            // Step 3: Fetch commits from GitHub API
            // CRITICAL: Fallback may be triggered here, setting degraded flag
            List<GithubCommitDto> commitDtos = githubClient.fetchCommits(
                    config.getGithubRepoUrl(),
                    config.getGithubAccessToken(),
                    100);

            // Check if fallback was triggered (degraded execution)
            boolean degraded = fallbackSignal.isDegraded();
            String degradationReason = fallbackSignal.getReason();

            log.info("Fetched {} GitHub commits for configId={}, degraded={}", 
                    commitDtos.size(), projectConfigId, degraded);

            // Step 4: Transform data
            List<UnifiedActivity> unifiedActivities = commitDtos.stream()
                    .map(dto -> dataMapper.githubCommitToUnifiedActivity(dto, projectConfigId))
                    .collect(Collectors.toList());

            List<GithubCommit> githubCommits = commitDtos.stream()
                    .map(dto -> dataMapper.githubCommitDtoToEntity(dto, projectConfigId))
                    .collect(Collectors.toList());

            // Step 5: Persist to DB
            int savedActivities = syncDataService.persistUnifiedActivities(unifiedActivities);
            syncDataService.persistGithubCommits(githubCommits);

            // Step 6: Update sync job status based on execution result
            long duration = System.currentTimeMillis() - startTime;
            
            if (degraded) {
                // Fallback triggered → PARTIAL_FAILURE
                syncDataService.markSyncJobAsPartialFailure(
                        syncJob.getId(), 
                        commitDtos.size(), 
                        savedActivities, 
                        degradationReason);
                syncMetrics.recordSyncPartialFailure(SyncJob.JobType.GITHUB_COMMITS, duration);
                
                log.warn("⚠️ GitHub sync PARTIAL_FAILURE for configId={}: reason={}", 
                        projectConfigId, degradationReason);
            } else {
                // Normal execution → COMPLETED (even if 0 records)
                syncDataService.completeSyncJob(syncJob.getId(), commitDtos.size(), savedActivities);
                syncMetrics.recordSyncSuccess(SyncJob.JobType.GITHUB_COMMITS, duration);
                
                log.info("✅ Completed GitHub sync for configId={}: fetched={}, saved={}, duration={}ms",
                        projectConfigId, commitDtos.size(), savedActivities, duration);
            }

                return CompletableFuture.completedFuture(SyncResultDto.builder()
                        .syncJobId(syncJob.getId())
                        .projectConfigId(projectConfigId)
                        .jobType(SyncJob.JobType.GITHUB_COMMITS.name())
                        .success(!degraded)
                        .degraded(degraded)
                        .recordsFetched(commitDtos.size())
                        .recordsSaved(savedActivities)
                        .durationMs(duration)
                        .errorMessage(degraded ? degradationReason : null)
                        .correlationId(correlationId)
                        .build());

            } catch (Exception e) {
                log.error("❌ Failed GitHub sync for configId={}: {}", projectConfigId, e.getMessage(), e);

                if (syncJob != null) {
                    syncDataService.failSyncJob(syncJob.getId(), e.getMessage());
                }

                syncMetrics.recordSyncFailure(SyncJob.JobType.GITHUB_COMMITS);

                return CompletableFuture.completedFuture(SyncResultDto.builder()
                        .syncJobId(syncJob != null ? syncJob.getId() : null)
                        .projectConfigId(projectConfigId)
                        .jobType(SyncJob.JobType.GITHUB_COMMITS.name())
                        .success(false)
                        .degraded(false)
                        .errorMessage(e.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .correlationId(correlationId)
                        .build());
            } finally {
                MDC.remove("correlationId");
            }
        }
    }
}
