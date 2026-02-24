-- Project Config Service - Initial Schema
-- Version: 1.0
-- Date: 2026-02-09
-- 
-- Creates project_configs table for storing encrypted Jira and GitHub credentials.
-- Implements soft delete pattern with 90-day retention.

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Main table: project_configs
CREATE TABLE project_configs (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    
    -- Group Reference (no FK constraint - microservices pattern)
    group_id BIGINT NOT NULL,
    
    -- Jira Configuration
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token_encrypted TEXT NOT NULL,
    
    -- GitHub Configuration
    github_repo_url VARCHAR(512) NOT NULL,
    github_token_encrypted TEXT NOT NULL,
    
    -- State Machine
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    last_verified_at TIMESTAMP NULL,
    invalid_reason TEXT NULL,
    
    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    
    -- Soft Delete
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,
    
    -- Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_state CHECK (state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED')),
    CONSTRAINT chk_state_deleted CHECK (
        (state = 'DELETED' AND deleted_at IS NOT NULL) OR
        (state != 'DELETED' AND deleted_at IS NULL)
    )
);

-- Indexes for Performance
CREATE INDEX idx_project_configs_group_id ON project_configs(group_id);
CREATE UNIQUE INDEX idx_project_configs_group_id_unique ON project_configs(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_configs_state ON project_configs(state) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_configs_deleted_at ON project_configs(deleted_at);
CREATE INDEX idx_project_configs_deleted_retention ON project_configs(deleted_at) WHERE deleted_at IS NOT NULL;

-- Comments
COMMENT ON TABLE project_configs IS 'Project configurations for Jira and GitHub integration';
COMMENT ON COLUMN project_configs.group_id IS 'Group ID from User-Group Service (no FK constraint)';
COMMENT ON COLUMN project_configs.jira_api_token_encrypted IS 'Encrypted Jira API token (AES-256-GCM format: {iv}:{ciphertext})';
COMMENT ON COLUMN project_configs.github_token_encrypted IS 'Encrypted GitHub PAT (AES-256-GCM format: {iv}:{ciphertext})';
COMMENT ON COLUMN project_configs.state IS 'State machine: DRAFT → VERIFIED/INVALID → DELETED';
COMMENT ON COLUMN project_configs.last_verified_at IS 'Timestamp of last successful verification';
COMMENT ON COLUMN project_configs.invalid_reason IS 'Error message if state = INVALID';
COMMENT ON COLUMN project_configs.deleted_at IS 'Soft delete timestamp (90-day retention)';
COMMENT ON COLUMN project_configs.deleted_by IS 'User ID who deleted the config';
COMMENT ON COLUMN project_configs.version IS 'Optimistic locking version';

