-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V5
-- Description: Create shedlock table for distributed locking
-- Purpose: Multi-replica safe scheduler execution
-- Author: Sync Service Team
-- Date: 2026-02-22
-- ==============================================

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

-- Index for performance
CREATE INDEX idx_shedlock_lock_until ON shedlock(lock_until);

-- Comments for documentation
COMMENT ON TABLE shedlock IS 'ShedLock distributed locking table for multi-instance scheduler safety';
COMMENT ON COLUMN shedlock.name IS 'Unique lock name (e.g., syncJiraIssues)';
COMMENT ON COLUMN shedlock.lock_until IS 'Lock expiration timestamp';
COMMENT ON COLUMN shedlock.locked_at IS 'When lock was acquired';
COMMENT ON COLUMN shedlock.locked_by IS 'Instance identifier that acquired lock';
