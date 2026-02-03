package com.example.project_configservice.repository;

import com.example.project_configservice.entity.ProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProjectConfig entity.
 * Note: @SQLRestriction on entity automatically filters soft-deleted records.
 */
@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
    
    /**
     * Find config by ID (soft-deleted configs are automatically filtered).
     */
    Optional<ProjectConfig> findById(UUID id);
    
    /**
     * Find config by group ID (soft-deleted configs are automatically filtered).
     */
    Optional<ProjectConfig> findByGroupId(UUID groupId);
    
    /**
     * Check if a config exists for a group (excludes soft-deleted).
     */
    boolean existsByGroupId(UUID groupId);
}
