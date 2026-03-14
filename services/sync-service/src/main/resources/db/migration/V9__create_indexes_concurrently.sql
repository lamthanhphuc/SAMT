-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- Version: V9
-- Create indexes during bootstrap
-- ==============================================

-- ==============================================
-- STEP 1: Drop renamed INVALID indexes
-- ==============================================

DROP INDEX IF EXISTS idx_sync_jobs_correlation_id_invalid_drop;
DROP INDEX IF EXISTS idx_sync_jobs_status_records_invalid_drop;

-- ==============================================
-- STEP 2: Create indexes
-- ==============================================

-- Index for correlation_id lookups (distributed tracing queries)
-- Partial index: Only non-NULL values (saves space)
-- Expected usage: WHERE correlation_id = 'SYNC-abc123'
CREATE INDEX IF NOT EXISTS idx_sync_jobs_correlation_id
    ON sync_jobs(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Composite index for status + records queries (analytics/reporting)
-- Partial index: Only SUCCESS/PARTIAL_FAILURE (most queried statuses)
-- Expected usage: WHERE status = 'SUCCESS' ORDER BY records_saved DESC
CREATE INDEX IF NOT EXISTS idx_sync_jobs_status_records
    ON sync_jobs(status, records_saved)
    WHERE status IN ('COMPLETED','PARTIAL_FAILURE');

-- ==============================================
-- END OF V9 - Index creation complete
-- ==============================================
