package com.example.syncservice.repository;

import com.example.syncservice.entity.UnifiedActivity;

import java.util.List;

/**
 * Custom repository interface for UnifiedActivity UPSERT operations.
 * Implements PostgreSQL-specific ON CONFLICT logic.
 */
public interface UnifiedActivityRepositoryCustom {

    /**
     * Upsert (insert or update) a batch of unified activities.
     * Uses PostgreSQL ON CONFLICT DO UPDATE for idempotent writes.
     * Automatically handles batching for large datasets (max 500 records per batch).
     *
     * @param activities List of activities to upsert
     * @return Number of rows affected
     */
    int upsertBatch(List<UnifiedActivity> activities);
}
