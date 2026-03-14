-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V6
-- Description: Add PARTIAL_FAILURE status to sync_jobs
-- Purpose: Distinguish degraded execution (fallback triggered) from normal completion
-- Author: Sync Service Team
-- Date: 2026-02-24
-- ==============================================

-- Drop existing status constraint
ALTER TABLE sync_jobs DROP CONSTRAINT IF EXISTS chk_status;

-- Add new constraint with PARTIAL_FAILURE status
ALTER TABLE sync_jobs ADD CONSTRAINT chk_status 
    CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL_FAILURE', 'FAILED'));

-- Update comment to reflect new status
COMMENT ON COLUMN sync_jobs.status IS 'Current status: RUNNING | COMPLETED | PARTIAL_FAILURE (degraded execution, fallback triggered) | FAILED (complete failure)';

-- ==============================================
-- SEMANTIC CLARIFICATION
-- ==============================================
-- COMPLETED:        Normal execution, success (including legitimate 0 records)
-- PARTIAL_FAILURE:  Fallback triggered, degraded execution (circuit open or retry exhausted)
--                   Data may be incomplete or missing due to external API unavailability
-- FAILED:           Exception thrown after retries, complete failure
--
-- Example scenarios:
-- - External API returns 0 records → COMPLETED (legitimate empty result)
-- - Circuit breaker open, fallback returns empty list → PARTIAL_FAILURE (degraded)
-- - Retry exhausted, fallback returns empty list → PARTIAL_FAILURE (degraded)
-- - Database exception, retry exhausted → FAILED (complete failure)
-- ==============================================

-- Index performance note: No index rebuild needed, status column VARCHAR indexed via composite indexes
-- Backward compatibility: Existing rows with ('RUNNING', 'COMPLETED', 'FAILED') remain valid
