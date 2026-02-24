-- flyway:transaction:none

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sync_jobs_correlation_id
    ON sync_jobs(correlation_id)
    WHERE correlation_id IS NOT NULL;
