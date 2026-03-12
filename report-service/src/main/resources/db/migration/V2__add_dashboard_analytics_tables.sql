TRUNCATE TABLE jira_issues RESTART IDENTITY;

ALTER TABLE jira_issues
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

CREATE TABLE IF NOT EXISTS unified_activities (
    id BIGSERIAL PRIMARY KEY,
    project_config_id UUID NOT NULL,
    source VARCHAR(20) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    description TEXT,
    author_email VARCHAR(255),
    author_name VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT uk_unified_activities_config_source_external UNIQUE (project_config_id, source, external_id)
);

CREATE INDEX IF NOT EXISTS idx_unified_activities_config ON unified_activities(project_config_id);
CREATE INDEX IF NOT EXISTS idx_unified_activities_source_type ON unified_activities(source, activity_type);
CREATE INDEX IF NOT EXISTS idx_unified_activities_author ON unified_activities(author_email);
CREATE INDEX IF NOT EXISTS idx_unified_activities_created_at ON unified_activities(created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_unified_activities_source'
    ) THEN
        ALTER TABLE unified_activities
            ADD CONSTRAINT chk_unified_activities_source CHECK (source IN ('JIRA', 'GITHUB'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_unified_activities_type'
    ) THEN
        ALTER TABLE unified_activities
            ADD CONSTRAINT chk_unified_activities_type CHECK (activity_type IN ('TASK', 'ISSUE', 'BUG', 'STORY', 'COMMIT', 'PULL_REQUEST'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS github_commits (
    id BIGSERIAL PRIMARY KEY,
    project_config_id UUID NOT NULL,
    commit_sha VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    committed_date TIMESTAMP NOT NULL,
    author_email VARCHAR(255),
    author_name VARCHAR(255),
    author_login VARCHAR(255),
    additions INTEGER DEFAULT 0,
    deletions INTEGER DEFAULT 0,
    total_changes INTEGER DEFAULT 0,
    files_changed INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    CONSTRAINT uk_github_commits_config_sha UNIQUE (project_config_id, commit_sha)
);

CREATE INDEX IF NOT EXISTS idx_github_commits_config ON github_commits(project_config_id);
CREATE INDEX IF NOT EXISTS idx_github_commits_author ON github_commits(author_email);
CREATE INDEX IF NOT EXISTS idx_github_commits_author_login ON github_commits(author_login);
CREATE INDEX IF NOT EXISTS idx_github_commits_date ON github_commits(committed_date DESC);

CREATE TABLE IF NOT EXISTS sync_jobs (
    id BIGSERIAL PRIMARY KEY,
    project_config_id UUID NOT NULL,
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

CREATE INDEX IF NOT EXISTS idx_sync_jobs_config_status ON sync_jobs(project_config_id, status);
CREATE INDEX IF NOT EXISTS idx_sync_jobs_completed_at ON sync_jobs(completed_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_sync_jobs_job_type'
    ) THEN
        ALTER TABLE sync_jobs
            ADD CONSTRAINT chk_sync_jobs_job_type CHECK (job_type IN ('JIRA_ISSUES', 'JIRA_SPRINTS', 'GITHUB_COMMITS', 'GITHUB_PRS'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_sync_jobs_status'
    ) THEN
        ALTER TABLE sync_jobs
            ADD CONSTRAINT chk_sync_jobs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL_FAILURE'));
    END IF;
END $$;