ALTER TABLE project_configs
ADD COLUMN jira_email VARCHAR(255);

COMMENT ON COLUMN project_configs.jira_email IS 'Jira account email used with API token for Atlassian Cloud Basic authentication';