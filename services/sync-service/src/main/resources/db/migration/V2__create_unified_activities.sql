-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V2
-- Description: Create unified_activities table
-- Purpose: Normalized activity data from Jira + GitHub for analytics
-- Author: Sync Service Team
-- Date: 2026-02-22
-- ==============================================

CREATE TABLE unified_activities (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    author_email VARCHAR(255),
    author_name VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(project_config_id, source, external_id)
);

-- Indexes for performance
CREATE INDEX idx_unified_activities_config ON unified_activities(project_config_id);
CREATE INDEX idx_unified_activities_source_type ON unified_activities(source, activity_type);
CREATE INDEX idx_unified_activities_author ON unified_activities(author_email);
CREATE INDEX idx_unified_activities_created_at ON unified_activities(created_at DESC);
CREATE INDEX idx_unified_activities_deleted_at ON unified_activities(deleted_at) WHERE deleted_at IS NOT NULL;

-- Constraints for data integrity
ALTER TABLE unified_activities ADD CONSTRAINT chk_source 
    CHECK (source IN ('JIRA', 'GITHUB'));

ALTER TABLE unified_activities ADD CONSTRAINT chk_activity_type 
    CHECK (activity_type IN ('TASK', 'ISSUE', 'BUG', 'STORY', 'COMMIT', 'PULL_REQUEST'));

-- Comments for documentation
COMMENT ON TABLE unified_activities IS 'Normalized activity data from Jira and GitHub for analytics/reporting';
COMMENT ON COLUMN unified_activities.project_config_id IS 'Reference to project_configs.id (logical FK, no physical constraint)';
COMMENT ON COLUMN unified_activities.source IS 'Data source: JIRA | GITHUB';
COMMENT ON COLUMN unified_activities.activity_type IS 'Type: TASK | ISSUE | BUG | STORY | COMMIT | PULL_REQUEST';
COMMENT ON COLUMN unified_activities.external_id IS 'Jira issue key (e.g., SWP391-123) or GitHub commit SHA/PR number';
COMMENT ON COLUMN unified_activities.author_email IS 'Email from Jira assignee or GitHub commit author';
COMMENT ON COLUMN unified_activities.deleted_at IS 'Soft delete timestamp (90-day retention policy)';
