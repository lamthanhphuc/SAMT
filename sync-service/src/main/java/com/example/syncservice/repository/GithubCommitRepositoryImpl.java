package com.example.syncservice.repository;

import com.example.syncservice.entity.GithubCommit;
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
 * Implementation of custom UPSERT operations for GithubCommit.
 * Uses native PostgreSQL ON CONFLICT for idempotent writes.
 * 
 * CRITICAL: Ensures no constraint violations on sync retry or circuit breaker recovery.
 * PERFORMANCE: True batch UPSERT with multi-row INSERT (single roundtrip per batch).
 */
@Repository
@Slf4j
public class GithubCommitRepositoryImpl implements GithubCommitRepositoryCustom {

    private static final int BATCH_SIZE = 500;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int upsertBatch(List<GithubCommit> commits) {
        if (commits == null || commits.isEmpty()) {
            return 0;
        }

        int totalAffected = 0;

        // Process in batches to prevent query size limit and memory issues
        for (int start = 0; start < commits.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, commits.size());
            List<GithubCommit> batch = commits.subList(start, end);
            totalAffected += executeBatchUpsert(batch);
        }

        // Flush and clear to prevent memory issues with large batches
        entityManager.flush();
        entityManager.clear();

        log.debug("Batch upserted {} GitHub commits in {} batches", totalAffected,
                (commits.size() + BATCH_SIZE - 1) / BATCH_SIZE);
        return totalAffected;
    }

    /**
     * Executes true batch UPSERT with multi-row INSERT.
     * 
     * PERFORMANCE: Single database roundtrip for entire batch (vs N roundtrips).
     * Example: 500 records = 1 query (< 0.5s) instead of 500 queries (5-10s).
     */
    private int executeBatchUpsert(List<GithubCommit> batch) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO github_commits (
                    project_config_id, commit_sha, message,
                    author_name, author_email, author_login,
                    committed_date,
                    additions, deletions, files_changed, total_changes,
                    created_at, updated_at
                ) VALUES 
                """);

        // Build multi-row VALUES clause: (?, ?, ...), (?, ?, ...), ...
        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            if (i < batch.size() - 1) {
                sql.append(",\n");
            }
        }

        sql.append("""
                
                ON CONFLICT (project_config_id, commit_sha)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    author_name = EXCLUDED.author_name,
                    author_email = EXCLUDED.author_email,
                    author_login = EXCLUDED.author_login,
                    committed_date = EXCLUDED.committed_date,
                    additions = EXCLUDED.additions,
                    deletions = EXCLUDED.deletions,
                    files_changed = EXCLUDED.files_changed,
                    total_changes = EXCLUDED.total_changes,
                    updated_at = EXCLUDED.updated_at
                """);

        Query query = entityManager.createNativeQuery(sql.toString());

        // Bind parameters sequentially for all rows
        int paramIndex = 1;
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTimestamp = Timestamp.valueOf(now);

        for (GithubCommit commit : batch) {
            query.setParameter(paramIndex++, commit.getProjectConfigId());
            query.setParameter(paramIndex++, commit.getCommitSha());
            query.setParameter(paramIndex++, commit.getMessage());
            query.setParameter(paramIndex++, commit.getAuthorName());
            query.setParameter(paramIndex++, commit.getAuthorEmail());
            query.setParameter(paramIndex++, commit.getAuthorLogin());
            query.setParameter(paramIndex++, commit.getCommittedDate() != null
                    ? Timestamp.valueOf(commit.getCommittedDate())
                    : nowTimestamp);
            query.setParameter(paramIndex++, commit.getAdditions() != null ? commit.getAdditions() : 0);
            query.setParameter(paramIndex++, commit.getDeletions() != null ? commit.getDeletions() : 0);
            query.setParameter(paramIndex++, commit.getFilesChanged() != null ? commit.getFilesChanged() : 0);
            query.setParameter(paramIndex++, commit.getTotalChanges() != null ? commit.getTotalChanges() : 0);
            query.setParameter(paramIndex++, commit.getCreatedAt() != null
                    ? Timestamp.valueOf(commit.getCreatedAt())
                    : nowTimestamp);
            query.setParameter(paramIndex++, commit.getUpdatedAt() != null
                    ? Timestamp.valueOf(commit.getUpdatedAt())
                    : nowTimestamp);
        }

        return query.executeUpdate();
    }

    @Override
    @Transactional
    public int upsert(GithubCommit commit) {
        // Map entity field names to actual database column names
        String sql = """
                INSERT INTO github_commits (
                    project_config_id, commit_sha, message,
                    author_name, author_email, committed_date,
                    additions, deletions, files_changed,
                    created_at, updated_at
                ) VALUES (
                    :projectConfigId, :commitSha, :commitMessage,
                    :authorName, :authorEmail, :committedDate,
                    :additions, :deletions, :filesChanged,
                    :createdAt, :updatedAt
                )
                ON CONFLICT (project_config_id, commit_sha)
                DO UPDATE SET
                    message = EXCLUDED.message,
                    author_name = EXCLUDED.author_name,
                    author_email = EXCLUDED.author_email,
                    additions = EXCLUDED.additions,
                    deletions = EXCLUDED.deletions,
                    files_changed = EXCLUDED.files_changed,
                    committed_date = EXCLUDED.committed_date,
                    updated_at = EXCLUDED.updated_at
                """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("projectConfigId", commit.getProjectConfigId());
        query.setParameter("commitSha", commit.getCommitSha());
        query.setParameter("commitMessage", commit.getMessage());
        query.setParameter("authorName", commit.getAuthorName());
        query.setParameter("authorEmail", commit.getAuthorEmail());
        query.setParameter("committedDate", Timestamp.valueOf(
                commit.getCommittedDate() != null ? commit.getCommittedDate() : LocalDateTime.now()));
        query.setParameter("additions", commit.getAdditions() != null ? commit.getAdditions() : 0);
        query.setParameter("deletions", commit.getDeletions() != null ? commit.getDeletions() : 0);
        query.setParameter("filesChanged", commit.getFilesChanged() != null ? commit.getFilesChanged() : 0);
        query.setParameter("createdAt", Timestamp.valueOf(
                commit.getCreatedAt() != null ? commit.getCreatedAt() : LocalDateTime.now()));
        query.setParameter("updatedAt", Timestamp.valueOf(
                commit.getUpdatedAt() != null ? commit.getUpdatedAt() : LocalDateTime.now()));

        return query.executeUpdate();
    }
}
