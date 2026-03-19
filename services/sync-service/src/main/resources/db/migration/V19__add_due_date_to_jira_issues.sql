-- Add Jira due date to support overdue calculations.
-- Jira Cloud: fields.duedate is ISO_LOCAL_DATE (yyyy-MM-dd).

ALTER TABLE jira_issues
    ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE INDEX IF NOT EXISTS idx_jira_issues_due_date ON jira_issues(due_date) WHERE due_date IS NOT NULL;

COMMENT ON COLUMN jira_issues.due_date IS 'Jira due date (fields.duedate)';

