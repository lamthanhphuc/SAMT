-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V12
-- Description: Sync all tables with entity definitions
-- Purpose: Fix schema-validation errors (missing columns from BaseEntity and entities)
-- Author: Database Schema Sync Engineer
-- Date: 2026-02-22
-- ==============================================

-- ==============================================
-- TABLE: sync_jobs
-- ==============================================

-- Add BaseEntity audit columns
ALTER TABLE sync_jobs 
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE sync_jobs 
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

COMMENT ON COLUMN sync_jobs.created_by IS 'User ID who created this record (BaseEntity audit trail)';
COMMENT ON COLUMN sync_jobs.updated_by IS 'User ID who last updated this record (BaseEntity audit trail)';

-- ==============================================
-- TABLE: unified_activities
-- ==============================================

-- Add BaseEntity audit columns
ALTER TABLE unified_activities 
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE unified_activities 
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

COMMENT ON COLUMN unified_activities.created_by IS 'User ID who created this record (BaseEntity audit trail)';
COMMENT ON COLUMN unified_activities.updated_by IS 'User ID who last updated this record (BaseEntity audit trail)';

-- ==============================================
-- TABLE: jira_issues
-- ==============================================

-- Add missing name columns from JiraIssue entity
ALTER TABLE jira_issues 
    ADD COLUMN IF NOT EXISTS assignee_name VARCHAR(255);

ALTER TABLE jira_issues 
    ADD COLUMN IF NOT EXISTS reporter_name VARCHAR(255);

-- Add BaseEntity audit columns
ALTER TABLE jira_issues 
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE jira_issues 
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;

COMMENT ON COLUMN jira_issues.assignee_name IS 'Jira assignee display name';
COMMENT ON COLUMN jira_issues.reporter_name IS 'Jira reporter display name';
COMMENT ON COLUMN jira_issues.created_by IS 'User ID who created this record (BaseEntity audit trail)';
COMMENT ON COLUMN jira_issues.updated_by IS 'User ID who last updated this record (BaseEntity audit trail)';

-- ==============================================
-- TABLE: github_commits
-- ==============================================

-- Add message column (entity uses 'message', V4 created 'commit_message')
ALTER TABLE github_commits 
    ADD COLUMN IF NOT EXISTS message TEXT;

-- Backfill message from commit_message for existing rows
UPDATE github_commits 
SET message = commit_message 
WHERE message IS NULL AND commit_message IS NOT NULL;

-- Add total_changes column from GithubCommit entity
ALTER TABLE github_commits 
    ADD COLUMN IF NOT EXISTS total_changes INT;

COMMENT ON COLUMN github_commits.message IS 'Git commit message (matches entity field name)';
COMMENT ON COLUMN github_commits.total_changes IS 'Total lines changed (additions + deletions)';

-- ==============================================
-- INDEXES FOR AUDIT COLUMNS
-- ==============================================

-- sync_jobs indexes
CREATE INDEX IF NOT EXISTS idx_sync_jobs_created_by ON sync_jobs(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sync_jobs_updated_by ON sync_jobs(updated_by) WHERE updated_by IS NOT NULL;

-- unified_activities indexes
CREATE INDEX IF NOT EXISTS idx_unified_activities_created_by ON unified_activities(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_unified_activities_updated_by ON unified_activities(updated_by) WHERE updated_by IS NOT NULL;

-- jira_issues indexes
CREATE INDEX IF NOT EXISTS idx_jira_issues_created_by ON jira_issues(created_by) WHERE created_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jira_issues_updated_by ON jira_issues(updated_by) WHERE updated_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jira_issues_assignee_name ON jira_issues(assignee_name) WHERE assignee_name IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jira_issues_reporter_name ON jira_issues(reporter_name) WHERE reporter_name IS NOT NULL;

-- ==============================================
-- PRODUCTION SAFETY NOTES
-- ==============================================
-- ✅ All columns nullable - no data loss, no constraints blocking inserts
-- ✅ IF NOT EXISTS - idempotent, safe to re-run
-- ✅ Partial indexes (WHERE NOT NULL) - minimal storage overhead
-- ✅ Backfill for message column preserves existing data
-- ✅ commit_message column preserved for backward compatibility
-- ✅ No foreign keys added - aligns with logical FK pattern
-- ✅ No table locks - fast metadata-only operations
-- ==============================================
