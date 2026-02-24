package com.example.syncservice.scheduler;

import com.example.syncservice.service.SyncOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Scheduler for sync jobs.
 * 
 * CRITICAL DESIGN:
 * - @SchedulerLock ensures only ONE instance executes (multi-replica safe)
 * - Scheduler delegates to service layer (NO business logic here)
 * - Correlation ID for tracing
 * - Can be disabled via configuration property
 * 
 * PRODUCTION SAFETY:
 * - With 2+ replicas, ShedLock prevents duplicate execution
 * - lockAtMostFor: Maximum lock duration (prevents stuck locks)
 * - lockAtLeastFor: Minimum lock duration (prevents too frequent execution)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "sync.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private final SyncOrchestrator syncOrchestrator;

    /**
     * Scheduled job: Sync Jira issues for all verified configs.
     * 
     * Default: Every 30 minutes
     * Lock: Max 25 minutes (allows 5min buffer before next execution)
     */
    @Scheduled(cron = "${sync.scheduler.jira-issues-cron:0 */30 * * * *}")
    @SchedulerLock(
            name = "syncJiraIssues",
            lockAtMostFor = "25m",
            lockAtLeastFor = "1m"
    )
    public void syncJiraIssues() {
        String correlationId = "SCHEDULER-JIRA-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            log.info("=== Starting scheduled Jira issues sync: correlationId={} ===", correlationId);
            syncOrchestrator.executeFullSync();
            log.info("=== Completed scheduled Jira issues sync: correlationId={} ===", correlationId);
        } catch (Exception e) {
            log.error("Error in scheduled Jira issues sync: {}", e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Scheduled job: Sync GitHub commits for all verified configs.
     * 
     * Default: Every 15 minutes
     * Lock: Max 13 minutes
     */
    @Scheduled(cron = "${sync.scheduler.github-commits-cron:0 */15 * * * *}")
    @SchedulerLock(
            name = "syncGithubCommits",
            lockAtMostFor = "13m",
            lockAtLeastFor = "30s"
    )
    public void syncGithubCommits() {
        String correlationId = "SCHEDULER-GITHUB-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("correlationId", correlationId);

        try {
            log.info("=== Starting scheduled GitHub commits sync: correlationId={} ===", correlationId);
            // Note: This calls executeFullSync which currently only does Jira
            // You would need to add a similar method for GitHub in SyncOrchestrator
            // For now, this demonstrates the pattern
            log.warn("GitHub sync not yet implemented in orchestrator");
            log.info("=== Completed scheduled GitHub commits sync: correlationId={} ===", correlationId);
        } catch (Exception e) {
            log.error("Error in scheduled GitHub commits sync: {}", e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Health check scheduled job.
     * Runs every minute to verify scheduler is alive.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    @SchedulerLock(
            name = "schedulerHealthCheck",
            lockAtMostFor = "50s",
            lockAtLeastFor = "10s"
    )
    public void healthCheck() {
        log.debug("Scheduler health check: OK");
    }
}
