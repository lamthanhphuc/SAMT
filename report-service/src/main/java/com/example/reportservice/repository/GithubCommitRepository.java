package com.example.reportservice.repository;

import com.example.reportservice.entity.GithubCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface GithubCommitRepository extends JpaRepository<GithubCommit, Long> {

    long countByProjectConfigIdInAndDeletedAtIsNull(List<UUID> projectConfigIds);

    long countByProjectConfigIdInAndAuthorEmailIgnoreCaseAndDeletedAtIsNull(List<UUID> projectConfigIds, String authorEmail);

    @Query("""
        select max(g.committedDate) from GithubCommit g
        where g.projectConfigId in :projectConfigIds
          and lower(coalesce(g.authorEmail, '')) = lower(:authorEmail)
          and g.deletedAt is null
        """)
    LocalDateTime findLastCommitAt(@Param("projectConfigIds") List<UUID> projectConfigIds,
                                   @Param("authorEmail") String authorEmail);

    @Query("""
        select count(distinct date(g.committedDate)) from GithubCommit g
        where g.projectConfigId in :projectConfigIds
          and lower(coalesce(g.authorEmail, '')) = lower(:authorEmail)
          and g.deletedAt is null
        """)
    long countActiveCommitDays(@Param("projectConfigIds") List<UUID> projectConfigIds,
                               @Param("authorEmail") String authorEmail);
}