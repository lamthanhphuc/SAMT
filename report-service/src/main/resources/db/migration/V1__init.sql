CREATE TABLE IF NOT EXISTS reports (
	report_id UUID PRIMARY KEY,
	project_config_id BIGINT NOT NULL,
	type VARCHAR(32) NOT NULL,
	file_path TEXT NOT NULL,
	created_by UUID NOT NULL,
	created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_project_config_id ON reports(project_config_id);
CREATE INDEX IF NOT EXISTS idx_reports_created_by ON reports(created_by);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports(created_at DESC);

CREATE TABLE IF NOT EXISTS jira_issues (
	id BIGSERIAL PRIMARY KEY,
	project_config_id BIGINT NOT NULL,
	issue_key VARCHAR(50) NOT NULL,
	issue_id VARCHAR(50) NOT NULL,
	summary VARCHAR(500) NOT NULL,
	description TEXT,
	issue_type VARCHAR(50),
	status VARCHAR(50),
	assignee_email VARCHAR(255),
	assignee_name VARCHAR(255),
	reporter_email VARCHAR(255),
	reporter_name VARCHAR(255),
	priority VARCHAR(50),
	created_at TIMESTAMPTZ,
	updated_at TIMESTAMPTZ,
	CONSTRAINT uk_jira_issues_config_key UNIQUE (project_config_id, issue_key)
);

CREATE INDEX IF NOT EXISTS idx_jira_issues_project_config_id ON jira_issues(project_config_id);
CREATE INDEX IF NOT EXISTS idx_jira_issues_status ON jira_issues(status);
