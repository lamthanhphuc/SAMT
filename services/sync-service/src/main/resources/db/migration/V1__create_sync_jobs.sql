-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V1
-- Description: Create sync_jobs table
-- Purpose: Track execution status of all background sync jobs
-- Author: Sync Service Team
-- Date: 2026-02-22
-- ==============================================

CREATE TABLE sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    items_processed INT DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by BIGINT
);

-- Indexes for performance
CREATE INDEX idx_sync_jobs_config_status ON sync_jobs(project_config_id, status);
CREATE INDEX idx_sync_jobs_created_at ON sync_jobs(created_at DESC);
CREATE INDEX idx_sync_jobs_job_type ON sync_jobs(job_type, status);
CREATE INDEX idx_sync_jobs_deleted_at ON sync_jobs(deleted_at) WHERE deleted_at IS NOT NULL;

-- Constraints for data integrity
ALTER TABLE sync_jobs ADD CONSTRAINT chk_job_type 
    CHECK (job_type IN ('JIRA_ISSUES', 'JIRA_SPRINTS', 'GITHUB_COMMITS', 'GITHUB_PRS'));

ALTER TABLE sync_jobs ADD CONSTRAINT chk_status 
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'));

-- Comments for documentation
COMMENT ON TABLE sync_jobs IS 'Tracks execution status of all background sync jobs';
COMMENT ON COLUMN sync_jobs.project_config_id IS 'Reference to project_configs.id (logical FK, no physical constraint)';
COMMENT ON COLUMN sync_jobs.job_type IS 'Type of sync job: JIRA_ISSUES | JIRA_SPRINTS | GITHUB_COMMITS | GITHUB_PRS';
COMMENT ON COLUMN sync_jobs.status IS 'Current status: RUNNING | COMPLETED | FAILED';
COMMENT ON COLUMN sync_jobs.items_processed IS 'Number of records fetched and saved';
COMMENT ON COLUMN sync_jobs.error_message IS 'Error details if status=FAILED';
COMMENT ON COLUMN sync_jobs.deleted_at IS 'Soft delete timestamp (90-day retention policy)';
COMMENT ON COLUMN sync_jobs.deleted_by IS 'User ID who performed soft delete';
