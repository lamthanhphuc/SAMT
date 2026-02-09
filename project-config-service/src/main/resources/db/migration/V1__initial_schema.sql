-- Project Config Service - Initial Schema
-- Table: project_configs

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE project_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id BIGINT NOT NULL UNIQUE,
    
    -- Jira configuration
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token_encrypted TEXT NOT NULL,
    
    -- GitHub configuration
    github_repo_url VARCHAR(512) NOT NULL,
    github_token_encrypted TEXT NOT NULL,
    
    -- State management
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    invalid_reason TEXT,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    
    -- Soft delete
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    
    -- Optimistic locking
    version INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_state CHECK (state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED'))
);

-- Indexes
CREATE INDEX idx_project_configs_group_id ON project_configs(group_id);
CREATE INDEX idx_project_configs_state ON project_configs(state);
CREATE INDEX idx_project_configs_deleted_at ON project_configs(deleted_at);

-- Comments
COMMENT ON TABLE project_configs IS 'Project configuration for Jira and GitHub integration';
COMMENT ON COLUMN project_configs.state IS 'DRAFT: Initial/after update, VERIFIED: Test passed, INVALID: Test failed, DELETED: Soft deleted';
COMMENT ON COLUMN project_configs.invalid_reason IS 'Reason why config is INVALID (verification failed)';
COMMENT ON COLUMN project_configs.deleted_at IS 'Soft delete timestamp, configs deleted after 90 days';
