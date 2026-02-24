-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- Version: V9 (Non-Transactional Part)
-- Create indexes with CONCURRENTLY (zero downtime)
-- ==============================================

-- Flyway directive: Run this migration outside a transaction
-- Required for DROP/CREATE INDEX CONCURRENTLY (PostgreSQL constraint)
-- flyway:transaction:none

-- ==============================================
-- STEP 1: Drop renamed INVALID indexes
-- ==============================================

DROP INDEX CONCURRENTLY IF EXISTS idx_sync_jobs_correlation_id_invalid_drop;
DROP INDEX CONCURRENTLY IF EXISTS idx_sync_jobs_status_records_invalid_drop;

-- ==============================================
-- STEP 2: Create indexes (zero downtime)
-- ==============================================

-- Index for correlation_id lookups (distributed tracing queries)
-- Partial index: Only non-NULL values (saves space)
-- Expected usage: WHERE correlation_id = 'SYNC-abc123'
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sync_jobs_correlation_id
    ON sync_jobs(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Composite index for status + records queries (analytics/reporting)
-- Partial index: Only SUCCESS/PARTIAL_FAILURE (most queried statuses)
-- Expected usage: WHERE status = 'SUCCESS' ORDER BY records_saved DESC
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sync_jobs_status_records
    ON sync_jobs(status, records_saved)
    WHERE status IN ('COMPLETED','PARTIAL_FAILURE');

-- ==============================================
-- PERFORMANCE NOTES
-- ==============================================
-- CREATE INDEX CONCURRENTLY characteristics:
-- - Does NOT block SELECT/INSERT/UPDATE/DELETE
-- - Requires TWO full table scans (cannot run in transaction)
-- - Takes longer than normal CREATE INDEX
-- - Safe for production with high traffic
-- - Automatically retries if failed (IF NOT EXISTS)
-- ==============================================

-- ==============================================
-- END OF V9 - Index creation complete
-- ==============================================
