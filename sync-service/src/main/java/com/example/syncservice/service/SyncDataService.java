package com.example.syncservice.service;

import com.example.syncservice.entity.*;
import com.example.syncservice.metrics.SyncMetrics;
import com.example.syncservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for database operations with transactional boundaries.
 * 
 * CRITICAL DESIGN:
 * - @Transactional methods are SHORT-LIVED
 * - NO external API calls inside these methods
 * - Batch operations use EntityManager flush/clear to prevent memory leaks
 * - UPSERT logic ensures idempotent writes
 * - Tracks constraint violations for production monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncDataService {

    private final SyncJobRepository syncJobRepository;
    private final UnifiedActivityRepository unifiedActivityRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final SyncMetrics syncMetrics;

    /**
     * Create and persist a new sync job.
     * Transaction duration: <100ms
     */
    @Transactional
    public SyncJob createSyncJob(Long projectConfigId, SyncJob.JobType jobType, String correlationId) {
        SyncJob job = SyncJob.builder()
                .projectConfigId(projectConfigId)
                .jobType(jobType)
                .status(SyncJob.JobStatus.RUNNING)
                .correlationId(correlationId)
                .build();
        job.markAsStarted(correlationId);

        SyncJob saved = syncJobRepository.save(job);
        log.debug("Created sync job id={} for configId={}, type={}", saved.getId(), projectConfigId, jobType);
        return saved;
    }

    /**
     * Mark sync job as completed.
     * Transaction duration: <50ms
     */
    @Transactional
    public void completeSyncJob(Long syncJobId, int recordsFetched, int recordsSaved) {
        SyncJob job = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalArgumentException("SyncJob not found: " + syncJobId));
        
        job.markAsCompleted(recordsFetched, recordsSaved);
        syncJobRepository.save(job);
        
        log.info("Sync job id={} completed: fetched={}, saved={}, duration={}ms",
                syncJobId, recordsFetched, recordsSaved, job.getDurationMs());
    }

    /**
     * Mark sync job as partial failure (degraded execution).
     * Transaction duration: <50ms
     * 
     * Used when fallback triggered (circuit breaker open or retry exhausted).
     */
    @Transactional
    public void markSyncJobAsPartialFailure(Long syncJobId, int recordsFetched, int recordsSaved, String errorMessage) {
        SyncJob job = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalArgumentException("SyncJob not found: " + syncJobId));
        
        job.markAsPartialFailure(recordsFetched, recordsSaved, errorMessage);
        syncJobRepository.save(job);
        
        log.warn("⚠️ Sync job id={} partial failure (degraded): fetched={}, saved={}, error={}",
                syncJobId, recordsFetched, recordsSaved, errorMessage);
    }

    /**
     * Mark sync job as failed.
     * Transaction duration: <50ms
     */
    @Transactional
    public void failSyncJob(Long syncJobId, String errorMessage) {
        SyncJob job = syncJobRepository.findById(syncJobId)
                .orElseThrow(() -> new IllegalArgumentException("SyncJob not found: " + syncJobId));
        
        job.markAsFailed(errorMessage);
        syncJobRepository.save(job);
        
        log.error("Sync job id={} failed: {}", syncJobId, errorMessage);
    }

    /**
     * Persist unified activities using UPSERT.
     * Transaction duration: 2-5 seconds for 1000 records
     * Tracks constraint violations for production monitoring.
     * 
     * @param activities List of unified activities
     * @return Number of rows affected
     */
    @Transactional
    public int persistUnifiedActivities(List<UnifiedActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return 0;
        }

        try {
            int affected = unifiedActivityRepository.upsertBatch(activities);
            log.debug("Persisted {} unified activities (upsert)", affected);
            return affected;
        } catch (DataIntegrityViolationException e) {
            // Only increment metric for UNIQUE constraint violations (SQLState 23505)
            // Do NOT count FK violations, NOT NULL, or other constraints
            if (e.getCause() instanceof PSQLException psqle && "23505".equals(psqle.getSQLState())) {
                log.error("❌ UNIQUE constraint violation in unified_activities: {}", e.getMessage());
                syncMetrics.recordConstraintViolation();
            } else {
                log.error("❌ Data integrity violation in unified_activities (non-unique): {}", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Persist Jira issues (denormalized) using UPSERT.
     * Transaction duration: 1-3 seconds for 500 records
     * Tracks constraint violations for production monitoring.
     * 
     * IDEMPOTENT: Safe for retry, circuit breaker recovery, and re-sync.
     * Uses ON CONFLICT DO UPDATE to prevent constraint violations.
     */
    @Transactional
    public void persistJiraIssues(List<JiraIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        try {
            // Use UPSERT to handle UNIQUE constraint on (project_config_id, issue_key)
            int affected = jiraIssueRepository.upsertBatch(issues);
            log.debug("Upserted {} Jira issues", affected);
        } catch (DataIntegrityViolationException e) {
            // Only increment metric for UNIQUE constraint violations (SQLState 23505)
            // Do NOT count FK violations, NOT NULL, or other constraints
            if (e.getCause() instanceof PSQLException psqle && "23505".equals(psqle.getSQLState())) {
                log.error("❌ UNIQUE constraint violation in jira_issues: {}", e.getMessage());
                syncMetrics.recordConstraintViolation();
            } else {
                log.error("❌ Data integrity violation in jira_issues (non-unique): {}", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Persist GitHub commits (denormalized) using UPSERT.
     * Transaction duration: 1-3 seconds for 500 records
     * Tracks constraint violations for production monitoring.
     * 
     * IDEMPOTENT: Safe for retry, circuit breaker recovery, and re-sync.
     * Uses ON CONFLICT DO UPDATE to prevent constraint violations.
     */
    @Transactional
    public void persistGithubCommits(List<GithubCommit> commits) {
        if (commits == null || commits.isEmpty()) {
            return;
        }

        try {
            // Use UPSERT to handle UNIQUE constraint on (project_config_id, commit_sha)
            int affected = githubCommitRepository.upsertBatch(commits);
            log.debug("Upserted {} GitHub commits", affected);
        } catch (DataIntegrityViolationException e) {
            // Only increment metric for UNIQUE constraint violations (SQLState 23505)
            // Do NOT count FK violations, NOT NULL, or other constraints
            if (e.getCause() instanceof PSQLException psqle && "23505".equals(psqle.getSQLState())) {
                log.error("❌ UNIQUE constraint violation in github_commits: {}", e.getMessage());
                syncMetrics.recordConstraintViolation();
            } else {
                log.error("❌ Data integrity violation in github_commits (non-unique): {}", e.getMessage());
            }
            throw e;
        }
    }
}
