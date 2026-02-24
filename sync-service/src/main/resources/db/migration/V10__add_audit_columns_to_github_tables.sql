-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V10
-- Description: Add missing audit columns to GitHub tables
-- Purpose: Fix schema validation error - BaseEntity audit fields
-- Author: Database Schema Compatibility Engineer
-- Date: 2026-02-22
-- ==============================================

-- Add created_by and updated_by to github_commits
-- Required by BaseEntity @CreatedBy and @LastModifiedBy annotations
ALTER TABLE github_commits 
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

-- Add created_by and updated_by to github_pull_requests
ALTER TABLE github_pull_requests 
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

-- Indexes for audit columns (github_commits)
CREATE INDEX IF NOT EXISTS idx_github_commits_created_by ON github_commits(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_github_commits_updated_by ON github_commits(updated_by) WHERE updated_by IS NOT NULL;

-- Indexes for audit columns (github_pull_requests)
CREATE INDEX IF NOT EXISTS idx_github_prs_created_by ON github_pull_requests(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_github_prs_updated_by ON github_pull_requests(updated_by) WHERE updated_by IS NOT NULL;

-- Comments for documentation
COMMENT ON COLUMN github_commits.created_by IS 'User ID who created this record (from BaseEntity audit trail)';
COMMENT ON COLUMN github_commits.updated_by IS 'User ID who last updated this record (from BaseEntity audit trail)';

COMMENT ON COLUMN github_pull_requests.created_by IS 'User ID who created this record (from BaseEntity audit trail)';
COMMENT ON COLUMN github_pull_requests.updated_by IS 'User ID who last updated this record (from BaseEntity audit trail)';

-- ==============================================
-- BACKWARD COMPATIBILITY NOTES
-- ==============================================
-- Columns are nullable - existing records will have NULL values
-- New records will populate these fields via Spring Data JPA auditing
-- No data migration needed - NULL is acceptable for historical records
-- Partial indexes used - only non-NULL values indexed for efficiency
-- ==============================================
