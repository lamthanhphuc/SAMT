package com.example.syncservice.repository;

import com.example.syncservice.entity.UnifiedActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for UnifiedActivity entity.
 * Extends custom repository for UPSERT operations.
 */
@Repository
public interface UnifiedActivityRepository extends JpaRepository<UnifiedActivity, Long>,
        UnifiedActivityRepositoryCustom {

    /**
     * Find all activities for a project config.
     */
    List<UnifiedActivity> findByProjectConfigIdAndDeletedAtIsNull(Long projectConfigId);

    /**
     * Find activities by source and project config.
     */
    List<UnifiedActivity> findByProjectConfigIdAndSourceAndDeletedAtIsNull(
            Long projectConfigId, UnifiedActivity.ActivitySource source);

    /**
     * Count activities created after a specific date.
     */
    @Query("SELECT COUNT(ua) FROM UnifiedActivity ua WHERE ua.projectConfigId = :configId " +
            "AND ua.createdAt > :since AND ua.deletedAt IS NULL")
    Long countRecentActivities(@Param("configId") Long configId, @Param("since") LocalDateTime since);
}
