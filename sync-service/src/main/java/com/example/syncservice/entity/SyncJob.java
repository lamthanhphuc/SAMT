package com.example.syncservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity representing a sync job execution.
 * Tracks status, duration, and error information for each sync operation.
 */
@Entity
@Table(name = "sync_jobs", indexes = {
        @Index(name = "idx_sync_jobs_config_status", columnList = "project_config_id,status"),
        @Index(name = "idx_sync_jobs_created_at", columnList = "created_at"),
        @Index(name = "idx_sync_jobs_job_type", columnList = "job_type"),
        @Index(name = "idx_sync_jobs_deleted_at", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private Long projectConfigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "records_fetched")
    private Integer recordsFetched;

    @Column(name = "records_saved")
    private Integer recordsSaved;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /**
     * Calculate execution duration in milliseconds.
     */
    public Long getDurationMs() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /**
     * Mark job as started.
     */
    public void markAsStarted(String correlationId) {
        this.status = JobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.correlationId = correlationId;
    }

    /**
     * Mark job as completed successfully.
     */
    public void markAsCompleted(int recordsFetched, int recordsSaved) {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.recordsFetched = recordsFetched;
        this.recordsSaved = recordsSaved;
    }

    /**
     * Mark job as partially failed (fallback triggered, degraded execution).
     * Used when external API call fails but fallback returns safe empty result.
     */
    public void markAsPartialFailure(int recordsFetched, int recordsSaved, String errorMessage) {
        this.status = JobStatus.PARTIAL_FAILURE;
        this.completedAt = LocalDateTime.now();
        this.recordsFetched = recordsFetched;
        this.recordsSaved = recordsSaved;
        this.errorMessage = errorMessage;
    }

    /**
     * Mark job as failed.
     */
    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    public enum JobType {
        JIRA_ISSUES,
        JIRA_SPRINTS,
        GITHUB_COMMITS,
        GITHUB_PRS
    }

    public enum JobStatus {
        RUNNING,
        COMPLETED,
        PARTIAL_FAILURE,  // Fallback triggered, degraded execution
        FAILED
    }
}
