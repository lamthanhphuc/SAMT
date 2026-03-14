package com.samt.projectconfig.repository;

import com.samt.projectconfig.entity.ProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProjectConfig entity.
 * 
 * Note: @SQLRestriction on entity auto-filters soft-deleted records.
 */
@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
    
    /**
     * Find config by group ID.
     * Auto-filters deleted_at IS NULL via @SQLRestriction.
     * 
     * @param groupId Group ID from User-Group Service
     * @return Optional containing config if found and not deleted
     */
    Optional<ProjectConfig> findByGroupId(Long groupId);
    
    /**
     * Check if config exists for group (excluding soft-deleted).
     * 
     * @param groupId Group ID
     * @return true if config exists and not deleted
     */
    boolean existsByGroupId(Long groupId);
    
    /**
     * Find all configs in a specific state (excluding soft-deleted).
     * 
     * @param state Config state (DRAFT, VERIFIED, INVALID, DELETED)
     * @return List of configs in specified state
     */
    List<ProjectConfig> findByState(String state);
    
    /**
     * Find configs deleted before a specific date.
     * Used by cleanup job to hard delete expired configs.
     * 
     * @param cutoffDate Retention cutoff date (now - 90 days)
     * @return List of configs to be permanently deleted
     */
    @Query("SELECT c FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoffDate")
    List<ProjectConfig> findDeletedConfigsOlderThan(@Param("cutoffDate") Instant cutoffDate);
    
    /**
     * Hard delete configs older than retention period.
     * BR-DELETE-03: 90-day retention policy.
     * 
     * @param cutoffDate Retention cutoff date
     * @return Number of configs permanently deleted
     */
    @Modifying
    @Query("DELETE FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoffDate")
    int hardDeleteExpiredConfigs(@Param("cutoffDate") Instant cutoffDate);
    
    /**
     * Find config by ID including soft-deleted (for admin restore operation).
     * 
     * @param id Config ID
     * @return Optional containing config even if soft-deleted
     */
    @Query(value = "SELECT * FROM project_configs WHERE id = :id", nativeQuery = true)
    Optional<ProjectConfig> findByIdIncludingDeleted(@Param("id") UUID id);
}
