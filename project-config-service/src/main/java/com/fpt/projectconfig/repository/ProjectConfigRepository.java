package com.fpt.projectconfig.repository;

import com.fpt.projectconfig.entity.ProjectConfig;
import com.fpt.projectconfig.entity.ProjectConfig.ConfigState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {

    /**
     * Tìm config theo group ID (excludes soft-deleted)
     */
    Optional<ProjectConfig> findByGroupId(UUID groupId);

    /**
     * Check xem group đã có config chưa (excludes soft-deleted)
     */
    boolean existsByGroupId(UUID groupId);

    /**
     * Tìm configs theo state (excludes soft-deleted)
     */
    List<ProjectConfig> findByState(ConfigState state);

    /**
     * Tìm config đã bị soft delete (dùng cho restore)
     */
    @Query("SELECT c FROM ProjectConfig c WHERE c.id = :id AND c.deletedAt IS NOT NULL")
    Optional<ProjectConfig> findDeletedById(@Param("id") UUID id);

    /**
     * Hard delete configs quá hạn retention (90 days)
     * Dùng cho cleanup scheduler
     */
    @Modifying
    @Query("DELETE FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoffDate")
    int deleteOldSoftDeletedConfigs(@Param("cutoffDate") Instant cutoffDate);
}
