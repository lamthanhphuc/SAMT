-- Convert sync-service references from legacy BIGINT project_config_id to UUID.
-- Sync tables contain derived data and can be rebuilt safely after this migration.

TRUNCATE TABLE
    unified_activities,
    jira_issues,
    jira_sprints,
    github_commits,
    github_pull_requests,
    sync_jobs
RESTART IDENTITY;

ALTER TABLE sync_jobs
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

ALTER TABLE unified_activities
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

ALTER TABLE jira_issues
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

ALTER TABLE jira_sprints
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

ALTER TABLE github_commits
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;

ALTER TABLE github_pull_requests
    ALTER COLUMN project_config_id TYPE UUID USING project_config_id::text::uuid;