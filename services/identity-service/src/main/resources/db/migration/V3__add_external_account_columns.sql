-- Add external integration account columns to users table
ALTER TABLE users
    ADD COLUMN jira_account_id VARCHAR(100) UNIQUE,
    ADD COLUMN github_username VARCHAR(100) UNIQUE;

CREATE INDEX idx_users_jira_account ON users(jira_account_id);
CREATE INDEX idx_users_github_username ON users(github_username);
