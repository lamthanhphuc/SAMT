package com.example.syncservice.metrics;

import com.example.syncservice.entity.SyncJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Metrics component for Prometheus monitoring.
 * 
 * Exposes:
 * - sync_jobs_total: Counter of total sync jobs by type and status
 * - sync_duration_seconds: Timer for sync job duration
 * - sync_failures_total: Counter of failed sync jobs by type
 * - sync_job_failure_count: Production metric for total failures (for alerting)
 * - sync_job_total_count: Production metric for total jobs started (for failure rate calculation)
 * - constraint_violation_count: Tracks database constraint violations
 * - parser_warning_count: Tracks timestamp parsing failures
 * 
 * Access metrics: http://localhost:8084/actuator/prometheus
 */
@Component
@Slf4j
public class SyncMetrics {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter jiraSuccessCounter;
    private final Counter jiraFailureCounter;
    private final Counter jiraPartialFailureCounter;  // NEW: Degraded execution
    private final Counter githubSuccessCounter;
    private final Counter githubFailureCounter;
    private final Counter githubPartialFailureCounter;  // NEW: Degraded execution

    // Production Metrics for Alerting
    private final Counter syncJobFailureCounter;
    private final Counter syncJobTotalCounter;  // NEW: Total jobs started
    private final Counter constraintViolationCounter;
    private final Counter parserWarningCounter;
    private final Counter recordsParsedCounter;  // NEW: Total records parsed (for accurate parser warning rate)
    private final Counter syncTasksRejectedCounter;  // NEW: Tasks rejected due to queue saturation
    private final Counter syncBatchPartialRejectionCounter;  // NEW: Batch with at least 1 rejection

    // Timers
    private final Timer jiraTimer;
    private final Timer githubTimer;

    public SyncMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.jiraSuccessCounter = Counter.builder("sync_jobs_total")
                .description("Total number of sync jobs")
                .tag("job_type", "jira_issues")
                .tag("status", "success")
                .register(meterRegistry);

        this.jiraFailureCounter = Counter.builder("sync_jobs_total")
                .tag("job_type", "jira_issues")
                .tag("status", "failure")
                .register(meterRegistry);

        // NEW: Partial failure counter (degraded execution)
        this.jiraPartialFailureCounter = Counter.builder("sync_jobs_total")
                .tag("job_type", "jira_issues")
                .tag("status", "partial_failure")
                .register(meterRegistry);

        this.githubSuccessCounter = Counter.builder("sync_jobs_total")
                .tag("job_type", "github_commits")
                .tag("status", "success")
                .register(meterRegistry);

        this.githubFailureCounter = Counter.builder("sync_jobs_total")
                .tag("job_type", "github_commits")
                .tag("status", "failure")
                .register(meterRegistry);

        // NEW: Partial failure counter (degraded execution)
        this.githubPartialFailureCounter = Counter.builder("sync_jobs_total")
                .tag("job_type", "github_commits")
                .tag("status", "partial_failure")
                .register(meterRegistry);

        // Production metrics for alerting
        this.syncJobFailureCounter = Counter.builder("sync_job_failure_count")
                .description("Number of failed sync jobs")
                .register(meterRegistry);

        this.syncJobTotalCounter = Counter.builder("sync_job_total_count")
                .description("Total number of sync jobs started (for failure rate calculation)")
                .register(meterRegistry);

        this.constraintViolationCounter = Counter.builder("constraint_violation_count")
                .description("Number of database constraint violations")
                .register(meterRegistry);

        this.parserWarningCounter = Counter.builder("parser_warning_count")
                .description("Number of timestamp parsing failures")
                .register(meterRegistry);

        this.recordsParsedCounter = Counter.builder("records_parsed_total")
                .description("Total number of records parsed (Jira issues + GitHub commits)")
                .register(meterRegistry);

        this.syncTasksRejectedCounter = Counter.builder("sync_tasks_rejected_total")
                .description("Number of sync tasks rejected due to queue full (AbortPolicy)")
                .register(meterRegistry);

        this.syncBatchPartialRejectionCounter = Counter.builder("sync_batch_partial_rejection_total")
                .description("Number of batches with at least one task rejected (partial failure)")
                .register(meterRegistry);

        // Initialize timers
        this.jiraTimer = Timer.builder("sync_duration_seconds")
                .description("Duration of sync operations")
                .tag("job_type", "jira_issues")
                .register(meterRegistry);

        this.githubTimer = Timer.builder("sync_duration_seconds")
                .tag("job_type", "github_commits")
                .register(meterRegistry);
    }

    /**
     * Record sync job started (called at orchestration entry point).
     * Used for calculating failure rate.
     * Increments on EVERY job start (success, failure, partial failure).
     */
    public void recordSyncJobStarted() {
        syncJobTotalCounter.increment();
        log.debug("Recorded sync job started");
    }

    /**
     * Record successful sync job.
     */
    public void recordSyncSuccess(SyncJob.JobType jobType, long durationMs) {
        switch (jobType) {
            case JIRA_ISSUES, JIRA_SPRINTS -> {
                jiraSuccessCounter.increment();
                jiraTimer.record(durationMs, TimeUnit.MILLISECONDS);
            }
            case GITHUB_COMMITS, GITHUB_PRS -> {
                githubSuccessCounter.increment();
                githubTimer.record(durationMs, TimeUnit.MILLISECONDS);
            }
        }
        log.debug("Recorded sync success: jobType={}, duration={}ms", jobType, durationMs);
    }

    /**
     * Record partial failure (degraded execution - fallback triggered).
     * This indicates the sync completed but with degraded service (e.g., circuit open, API unavailable).
     */
    public void recordSyncPartialFailure(SyncJob.JobType jobType, long durationMs) {
        switch (jobType) {
            case JIRA_ISSUES, JIRA_SPRINTS -> {
                jiraPartialFailureCounter.increment();
                jiraTimer.record(durationMs, TimeUnit.MILLISECONDS);
            }
            case GITHUB_COMMITS, GITHUB_PRS -> {
                githubPartialFailureCounter.increment();
                githubTimer.record(durationMs, TimeUnit.MILLISECONDS);
            }
        }
        log.warn("⚠️ Recorded sync partial failure (degraded): jobType={}, duration={}ms", jobType, durationMs);
    }

    /**
     * Record failed sync job (complete failure - exception thrown).
     */
    public void recordSyncFailure(SyncJob.JobType jobType) {
        switch (jobType) {
            case JIRA_ISSUES, JIRA_SPRINTS -> jiraFailureCounter.increment();
            case GITHUB_COMMITS, GITHUB_PRS -> githubFailureCounter.increment();
        }
        // Increment production failure counter for alerting
        syncJobFailureCounter.increment();
        log.debug("Recorded sync failure: jobType={}", jobType);
    }

    /**
     * Record UNIQUE constraint violation from database operations.
     * ONLY increments when SQLState = 23505 (unique_violation).
     * Does NOT count FK violations, NOT NULL, or other constraint types.
     * 
     * This metric indicates UPSERT logic failure or race conditions.
     * Expected: 0 in normal operation.
     */
    public void recordConstraintViolation() {
        constraintViolationCounter.increment();
        log.warn("⚠️ Recorded UNIQUE constraint violation (SQLState 23505)");
    }

    /**
     * Record parser warning when timestamp parsing fails.
     * Called when external API timestamp cannot be parsed and fallback is used.
     * Log should include: field name, record identifier, and raw timestamp value.
     */
    public void recordParserWarning() {
        parserWarningCounter.increment();
        // Log is already done at call site with contextual data
    }

    /**
     * Record sync task rejection (queue full).
     * Called when RejectedExecutionException is thrown by AbortPolicy.
     * This indicates thread pool saturation - requires capacity increase.
     */
    public void recordSyncRejection() {
        syncTasksRejectedCounter.increment();
        log.error("❌ CRITICAL: Sync task rejected - queue full (capacity exhausted)");
    }

    /**
     * Record batch partial rejection (at least 1 task rejected, but batch continued).
     * Called at end of batch execution if rejectionCount > 0.
     * Provides batch-level observability for degraded execution.
     */
    public void recordSyncBatchPartialRejection(int rejectionCount, int totalConfigs) {
        syncBatchPartialRejectionCounter.increment();
        log.warn("⚠️ Batch partial rejection: {}/{} tasks rejected", rejectionCount, totalConfigs);
    }

    /**
     * Record one record parsed (Jira issue or GitHub commit).
     * Called BEFORE timestamp parsing to ensure accurate denominator for parser warning rate.
     */
    public void recordRecordParsed() {
        recordsParsedCounter.increment();
    }

    /**
     * Register thread pool metrics for monitoring (from AsyncConfig executor).
     */
    public void registerThreadPoolMetrics(String executorName, 
                                          java.util.concurrent.ThreadPoolExecutor executor) {
        io.micrometer.core.instrument.Gauge.builder("thread_pool_active", executor,
                        java.util.concurrent.ThreadPoolExecutor::getActiveCount)
                .tag("executor", executorName)
                .description("Active thread count")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("thread_pool_queue_size", executor,
                        e -> e.getQueue().size())
                .tag("executor", executorName)
                .description("Queue size")
                .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("thread_pool_completed_tasks", executor,
                        java.util.concurrent.ThreadPoolExecutor::getCompletedTaskCount)
                .tag("executor", executorName)
                .description("Completed task count")
                .register(meterRegistry);
    }
}
