package com.example.syncservice.repository;

import com.example.syncservice.entity.JiraIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for JiraIssue entity.
 * Extends custom interface for UPSERT operations.
 */
@Repository
public interface JiraIssueRepository extends JpaRepository<JiraIssue, Long>, JiraIssueRepositoryCustom {

    List<JiraIssue> findByProjectConfigIdAndDeletedAtIsNull(UUID projectConfigId);

    Optional<JiraIssue> findByProjectConfigIdAndIssueKey(UUID projectConfigId, String issueKey);
}
