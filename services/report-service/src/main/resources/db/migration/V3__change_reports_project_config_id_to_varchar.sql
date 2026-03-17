ALTER TABLE reports
    ALTER COLUMN project_config_id TYPE VARCHAR(64) USING project_config_id::text;

DROP INDEX IF EXISTS idx_reports_project_config_id;
CREATE INDEX IF NOT EXISTS idx_reports_project_config_id ON reports(project_config_id);
