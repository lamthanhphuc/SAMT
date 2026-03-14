package com.example.reportservice.repository;

import com.example.reportservice.entity.UnifiedActivity;
import com.example.reportservice.entity.UnifiedActivity.ActivitySource;
import com.example.reportservice.entity.UnifiedActivity.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UnifiedActivityRepository extends JpaRepository<UnifiedActivity, Long> {

    @Query("""
        select u from UnifiedActivity u
        where u.projectConfigId = :projectConfigId
          and (:source is null or u.source = :source)
          and u.deletedAt is null
        order by u.createdAt desc, u.id desc
        """)
    Page<UnifiedActivity> findRecentActivities(@Param("projectConfigId") UUID projectConfigId,
                                               @Param("source") ActivitySource source,
                                               Pageable pageable);

    long countByProjectConfigIdInAndSourceAndActivityTypeAndDeletedAtIsNull(List<UUID> projectConfigIds,
                                                                            ActivitySource source,
                                                                            ActivityType activityType);

    List<UnifiedActivity> findByProjectConfigIdInAndActivityTypeAndAuthorEmailIgnoreCaseAndDeletedAtIsNull(List<UUID> projectConfigIds,
                                                        ActivityType activityType,
                                                        String authorEmail);

    @Query("""
        select count(u) from UnifiedActivity u
        where u.projectConfigId in :projectConfigIds
          and u.activityType = :activityType
          and lower(coalesce(u.authorEmail, '')) = lower(:authorEmail)
          and u.deletedAt is null
        """)
    long countByProjectConfigsAndActivityTypeAndAuthorEmail(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                                            @Param("activityType") ActivityType activityType,
                                                            @Param("authorEmail") String authorEmail);

    @Query("""
        select count(u) from UnifiedActivity u
        where u.projectConfigId in :projectConfigIds
          and u.activityType = :activityType
          and lower(coalesce(u.authorEmail, '')) = lower(:authorEmail)
          and lower(coalesce(u.status, '')) = lower(:status)
          and u.deletedAt is null
        """)
    long countByProjectConfigsAndActivityTypeAndAuthorEmailAndStatus(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                                                     @Param("activityType") ActivityType activityType,
                                                                     @Param("authorEmail") String authorEmail,
                                                                     @Param("status") String status);

    @Query("""
        select u from UnifiedActivity u
        where u.projectConfigId in :projectConfigIds
          and lower(coalesce(u.authorEmail, '')) = lower(:authorEmail)
          and u.deletedAt is null
        order by u.updatedAt desc, u.id desc
        """)
    List<UnifiedActivity> findRecentHighlights(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                               @Param("authorEmail") String authorEmail,
                                               Pageable pageable);
}