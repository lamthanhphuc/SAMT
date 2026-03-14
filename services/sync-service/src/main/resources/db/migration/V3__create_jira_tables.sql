-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V3
-- Description: Create Jira tables (jira_issues, jira_sprints)
-- Purpose: Store raw Jira data before normalization
-- Author: Sync Service Team
-- Date: 2026-02-22
-- ==============================================

-- Table: jira_issues
CREATE TABLE jira_issues (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    issue_key VARCHAR(50) NOT NULL,
    issue_id VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    description TEXT,
    issue_type VARCHAR(50),
    status VARCHAR(50),
    priority VARCHAR(50),
    assignee_email VARCHAR(255),
    reporter_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(project_config_id, issue_key)
);

-- Indexes for jira_issues
CREATE INDEX idx_jira_issues_config ON jira_issues(project_config_id);
CREATE INDEX idx_jira_issues_key ON jira_issues(issue_key);
CREATE INDEX idx_jira_issues_assignee ON jira_issues(assignee_email);
CREATE INDEX idx_jira_issues_status ON jira_issues(status);
CREATE INDEX idx_jira_issues_deleted_at ON jira_issues(deleted_at) WHERE deleted_at IS NOT NULL;

-- Comments for jira_issues
COMMENT ON TABLE jira_issues IS 'Raw Jira issue data (before normalization to unified_activities)';
COMMENT ON COLUMN jira_issues.issue_key IS 'Jira issue key (e.g., SWP391-123)';
COMMENT ON COLUMN jira_issues.issue_id IS 'Jira numeric issue ID';

-- Table: jira_sprints
CREATE TABLE jira_sprints (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    sprint_id VARCHAR(50) NOT NULL,
    sprint_name VARCHAR(255) NOT NULL,
    state VARCHAR(50),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    complete_date TIMESTAMP,
    goal TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(project_config_id, sprint_id)
);

-- Indexes for jira_sprints
CREATE INDEX idx_jira_sprints_config ON jira_sprints(project_config_id);
CREATE INDEX idx_jira_sprints_state ON jira_sprints(state);
CREATE INDEX idx_jira_sprints_deleted_at ON jira_sprints(deleted_at) WHERE deleted_at IS NOT NULL;

-- Constraints for jira_sprints
ALTER TABLE jira_sprints ADD CONSTRAINT chk_sprint_state 
    CHECK (state IN ('future', 'active', 'closed'));

-- Comments for jira_sprints
COMMENT ON TABLE jira_sprints IS 'Raw Jira sprint data';
COMMENT ON COLUMN jira_sprints.state IS 'Sprint state: future | active | closed';
COMMENT ON COLUMN jira_sprints.goal IS 'Sprint goal/objective';
