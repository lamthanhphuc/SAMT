-- Baseline schema marker for analysis-service.
-- Keep service schema ownership explicit even when no domain tables are required yet.
CREATE TABLE IF NOT EXISTS analysis_schema_marker (
    id SMALLINT PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO analysis_schema_marker (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
