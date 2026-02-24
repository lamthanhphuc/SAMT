package com.example.syncservice.repository;

import com.example.syncservice.entity.JiraIssue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of custom UPSERT operations for JiraIssue.
 * Uses native PostgreSQL ON CONFLICT for idempotent writes.
 * 
 * CRITICAL: Ensures no constraint violations on sync retry or circuit breaker recovery.
 * PERFORMANCE: True batch UPSERT with multi-row INSERT (single roundtrip per batch).
 */
@Repository
@Slf4j
public class JiraIssueRepositoryImpl implements JiraIssueRepositoryCustom {

    private static final int BATCH_SIZE = 500;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int upsertBatch(List<JiraIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }

        int totalAffected = 0;

        // Process in batches to prevent query size limit and memory issues
        for (int start = 0; start < issues.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, issues.size());
            List<JiraIssue> batch = issues.subList(start, end);
            totalAffected += executeBatchUpsert(batch);
        }

        // Flush and clear to prevent memory issues with large batches
        entityManager.flush();
        entityManager.clear();

        log.debug("Batch upserted {} Jira issues in {} batches", totalAffected, 
                (issues.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        return totalAffected;
    }

    /**
     * Executes true batch UPSERT with multi-row INSERT.
     * 
     * PERFORMANCE: Single database roundtrip for entire batch (vs N roundtrips).
     * Example: 500 records = 1 query (< 0.5s) instead of 500 queries (5-10s).
     */
    private int executeBatchUpsert(List<JiraIssue> batch) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO jira_issues (
                    project_config_id, issue_key, issue_id, summary, description,
                    issue_type, status, priority,
                    assignee_email, assignee_name,
                    reporter_email, reporter_name,
                    created_at, updated_at
                ) VALUES 
                """);

        // Build multi-row VALUES clause: (?, ?, ...), (?, ?, ...), ...
        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            if (i < batch.size() - 1) {
                sql.append(",\n");
            }
        }

        sql.append("""
                
                ON CONFLICT (project_config_id, issue_key)
                DO UPDATE SET
                    issue_id = EXCLUDED.issue_id,
                    summary = EXCLUDED.summary,
                    description = EXCLUDED.description,
                    issue_type = EXCLUDED.issue_type,
                    status = EXCLUDED.status,
                    priority = EXCLUDED.priority,
                    assignee_email = EXCLUDED.assignee_email,
                    assignee_name = EXCLUDED.assignee_name,
                    reporter_email = EXCLUDED.reporter_email,
                    reporter_name = EXCLUDED.reporter_name,
                    updated_at = EXCLUDED.updated_at
                """);

        Query query = entityManager.createNativeQuery(sql.toString());

        // Bind parameters sequentially for all rows
        int paramIndex = 1;
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTimestamp = Timestamp.valueOf(now);

        for (JiraIssue issue : batch) {
            query.setParameter(paramIndex++, issue.getProjectConfigId());
            query.setParameter(paramIndex++, issue.getIssueKey());
            query.setParameter(paramIndex++, issue.getIssueId());
            query.setParameter(paramIndex++, issue.getSummary());
            query.setParameter(paramIndex++, issue.getDescription());
            query.setParameter(paramIndex++, issue.getIssueType());
            query.setParameter(paramIndex++, issue.getStatus());
            query.setParameter(paramIndex++, issue.getPriority());
            query.setParameter(paramIndex++, issue.getAssigneeEmail());
            query.setParameter(paramIndex++, issue.getAssigneeName());
            query.setParameter(paramIndex++, issue.getReporterEmail());
            query.setParameter(paramIndex++, issue.getReporterName());
            query.setParameter(paramIndex++, issue.getCreatedAt() != null 
                    ? Timestamp.valueOf(issue.getCreatedAt()) 
                    : nowTimestamp);
            query.setParameter(paramIndex++, issue.getUpdatedAt() != null 
                    ? Timestamp.valueOf(issue.getUpdatedAt()) 
                    : nowTimestamp);
        }

        return query.executeUpdate();
    }

    @Override
    @Transactional
    public int upsert(JiraIssue issue) {
        // Map entity field names to actual database column names
        String sql = """
                INSERT INTO jira_issues (
                    project_config_id, issue_key, issue_id, summary, description,
                    issue_type, status, priority,
                    assignee_email, reporter_email,
                    created_at, updated_at
                ) VALUES (
                    :projectConfigId, :issueKey, :issueId, :summary, :description,
                    :issueType, :status, :priority,
                    :assigneeEmail, :reporterEmail,
                    :createdAt, :updatedAt
                )
                ON CONFLICT (project_config_id, issue_key)
                DO UPDATE SET
                    issue_id = EXCLUDED.issue_id,
                    summary = EXCLUDED.summary,
                    description = EXCLUDED.description,
                    issue_type = EXCLUDED.issue_type,
                    status = EXCLUDED.status,
                    priority = EXCLUDED.priority,
                    assignee_email = EXCLUDED.assignee_email,
                    reporter_email = EXCLUDED.reporter_email,
                    updated_at = EXCLUDED.updated_at
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("projectConfigId", issue.getProjectConfigId());
        query.setParameter("issueKey", issue.getIssueKey());
        query.setParameter("issueId", issue.getIssueId());
        query.setParameter("summary", issue.getSummary());
        query.setParameter("description", issue.getDescription());
        query.setParameter("issueType", issue.getIssueType());
        query.setParameter("status", issue.getStatus());
        query.setParameter("priority", issue.getPriority());
        query.setParameter("assigneeEmail", issue.getAssigneeEmail());
        query.setParameter("reporterEmail", issue.getReporterEmail());
        query.setParameter("createdAt", Timestamp.valueOf(
                issue.getCreatedAt() != null ? issue.getCreatedAt() : LocalDateTime.now()));
        query.setParameter("updatedAt", Timestamp.valueOf(
                issue.getUpdatedAt() != null ? issue.getUpdatedAt() : LocalDateTime.now()));

        return query.executeUpdate();
    }
}
