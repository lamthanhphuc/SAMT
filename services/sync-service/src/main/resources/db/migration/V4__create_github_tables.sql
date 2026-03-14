-- ==============================================
-- SYNC SERVICE DATABASE MIGRATION
-- ==============================================
-- Version: V4
-- Description: Create GitHub tables (github_commits, github_pull_requests)
-- Purpose: Store raw GitHub data before normalization
-- Author: Sync Service Team
-- Date: 2026-02-22
-- ==============================================

-- Table: github_commits
CREATE TABLE github_commits (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    commit_sha VARCHAR(40) NOT NULL,
    commit_message TEXT NOT NULL,
    author_name VARCHAR(255),
    author_email VARCHAR(255),
    committed_date TIMESTAMP NOT NULL,
    additions INT DEFAULT 0,
    deletions INT DEFAULT 0,
    files_changed INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(project_config_id, commit_sha)
);

-- Indexes for github_commits
CREATE INDEX idx_github_commits_config ON github_commits(project_config_id);
CREATE INDEX idx_github_commits_sha ON github_commits(commit_sha);
CREATE INDEX idx_github_commits_author ON github_commits(author_email);
CREATE INDEX idx_github_commits_date ON github_commits(committed_date DESC);
CREATE INDEX idx_github_commits_deleted_at ON github_commits(deleted_at) WHERE deleted_at IS NOT NULL;

-- Comments for github_commits
COMMENT ON TABLE github_commits IS 'Raw GitHub commit data (before normalization to unified_activities)';
COMMENT ON COLUMN github_commits.commit_sha IS 'Git commit SHA (40 characters)';
COMMENT ON COLUMN github_commits.additions IS 'Lines of code added';
COMMENT ON COLUMN github_commits.deletions IS 'Lines of code deleted';
COMMENT ON COLUMN github_commits.files_changed IS 'Number of files modified';

-- Table: github_pull_requests
CREATE TABLE github_pull_requests (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,
    pr_number INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    state VARCHAR(20),
    author_login VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    merged_at TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    UNIQUE(project_config_id, pr_number)
);

-- Indexes for github_pull_requests
CREATE INDEX idx_github_prs_config ON github_pull_requests(project_config_id);
CREATE INDEX idx_github_prs_number ON github_pull_requests(pr_number);
CREATE INDEX idx_github_prs_state ON github_pull_requests(state);
CREATE INDEX idx_github_prs_author ON github_pull_requests(author_login);
CREATE INDEX idx_github_prs_deleted_at ON github_pull_requests(deleted_at) WHERE deleted_at IS NOT NULL;

-- Constraints for github_pull_requests
ALTER TABLE github_pull_requests ADD CONSTRAINT chk_pr_state 
    CHECK (state IN ('open', 'closed', 'merged'));

-- Comments for github_pull_requests
COMMENT ON TABLE github_pull_requests IS 'Raw GitHub pull request data (before normalization to unified_activities)';
COMMENT ON COLUMN github_pull_requests.pr_number IS 'GitHub PR number (unique within repository)';
COMMENT ON COLUMN github_pull_requests.state IS 'PR state: open | closed | merged';
COMMENT ON COLUMN github_pull_requests.author_login IS 'GitHub username who created the PR';
