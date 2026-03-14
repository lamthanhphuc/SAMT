-- Expand unified activity text capacity for long GitHub commit messages.
-- Description remains TEXT; title is widened to accommodate long summaries safely.

ALTER TABLE unified_activities
    ALTER COLUMN title TYPE VARCHAR(1000);

ALTER TABLE unified_activities
    ALTER COLUMN description TYPE TEXT;

COMMENT ON COLUMN unified_activities.title IS 'Activity title/summary truncated to 1000 characters when needed';
COMMENT ON COLUMN unified_activities.description IS 'Full activity description/message payload';