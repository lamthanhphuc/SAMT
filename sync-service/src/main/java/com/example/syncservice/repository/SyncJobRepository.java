package com.example.syncservice.repository;

import com.example.syncservice.entity.SyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SyncJob entity.
 * Provides methods to track sync job execution status.
 */
@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    /**
     * Find all sync jobs for a specific project config.
     */
    List<SyncJob> findByProjectConfigIdAndDeletedAtIsNull(Long projectConfigId);

    /**
     * Find recent sync jobs.
     */
    @Query("SELECT sj FROM SyncJob sj WHERE sj.deletedAt IS NULL " +
            "AND sj.createdAt > :since ORDER BY sj.createdAt DESC")
    List<SyncJob> findRecentJobs(@Param("since") LocalDateTime since);

    /**
     * Find last successful sync job for a config and job type.
     */
    @Query("SELECT sj FROM SyncJob sj WHERE sj.projectConfigId = :configId " +
            "AND sj.jobType = :jobType AND sj.status = 'COMPLETED' " +
            "AND sj.deletedAt IS NULL ORDER BY sj.completedAt DESC LIMIT 1")
    Optional<SyncJob> findLastSuccessfulSync(@Param("configId") Long configId,
                                              @Param("jobType") SyncJob.JobType jobType);

    /**
     * Count failed jobs in last 24 hours.
     */
    @Query("SELECT COUNT(sj) FROM SyncJob sj WHERE sj.status = 'FAILED' " +
            "AND sj.createdAt > :since AND sj.deletedAt IS NULL")
    Long countRecentFailures(@Param("since") LocalDateTime since);
}
