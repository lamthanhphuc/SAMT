package com.example.syncservice.repository;

import com.example.syncservice.entity.UnifiedActivity;
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
 * Implementation of custom UPSERT operations for UnifiedActivity.
 * Uses native PostgreSQL ON CONFLICT for idempotent writes.
 * 
 * CRITICAL: This ensures no duplicate data even if sync runs multiple times.
 */
@Repository
@Slf4j
public class UnifiedActivityRepositoryImpl implements UnifiedActivityRepositoryCustom {

    private static final int MAX_BATCH_SIZE = 500;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public int upsertBatch(List<UnifiedActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return 0;
        }

        int totalAffected = 0;

        for (int start = 0; start < activities.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, activities.size());
            List<UnifiedActivity> batch = activities.subList(start, end);
            totalAffected += executeBatchUpsert(batch);
        }

        log.debug("Batch upserted {} unified activities", totalAffected);
        return totalAffected;
    }

    private int executeBatchUpsert(List<UnifiedActivity> batch) {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO unified_activities (
                    project_config_id, source, activity_type, external_id, 
                    title, description, author_email, author_name, status,
                    created_at, updated_at, created_by, updated_by
                ) VALUES 
                """);

        for (int i = 0; i < batch.size(); i++) {
            sql.append("(?, CAST(? AS VARCHAR), CAST(? AS VARCHAR), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            if (i < batch.size() - 1) {
                sql.append(",\n");
            }
        }

        sql.append("""
                
                ON CONFLICT (project_config_id, source, external_id) 
                DO UPDATE SET
                    title = EXCLUDED.title,
                    description = EXCLUDED.description,
                    author_email = EXCLUDED.author_email,
                    author_name = EXCLUDED.author_name,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by
                """);

        Query query = entityManager.createNativeQuery(sql.toString());

        int paramIndex = 1;
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTimestamp = Timestamp.valueOf(now);

        for (UnifiedActivity activity : batch) {
            query.setParameter(paramIndex++, activity.getProjectConfigId());
            query.setParameter(paramIndex++, activity.getSource().name());
            query.setParameter(paramIndex++, activity.getActivityType().name());
            query.setParameter(paramIndex++, activity.getExternalId());
            query.setParameter(paramIndex++, activity.getTitle());
            query.setParameter(paramIndex++, activity.getDescription());
            query.setParameter(paramIndex++, activity.getAuthorEmail());
            query.setParameter(paramIndex++, activity.getAuthorName());
            query.setParameter(paramIndex++, activity.getStatus());
            query.setParameter(paramIndex++, activity.getCreatedAt() != null 
                ? Timestamp.valueOf(activity.getCreatedAt()) 
                : nowTimestamp);
            query.setParameter(paramIndex++, nowTimestamp);
            query.setParameter(paramIndex++, activity.getCreatedBy());
            query.setParameter(paramIndex++, activity.getUpdatedBy());
        }

        return query.executeUpdate();
    }
}
