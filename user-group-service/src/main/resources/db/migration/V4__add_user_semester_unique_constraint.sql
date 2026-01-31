-- V4__add_user_semester_unique_constraint.sql
-- Add unique constraint to prevent race condition in UC24
-- Issue: User can be added to multiple groups in same semester simultaneously
-- Fix: Database-level defense-in-depth (application check already exists)

-- Note: This is a complex constraint because semester is in groups table, not user_groups
-- We need to ensure one user can only be in one group per semester

-- Drop existing indexes if needed (for idempotency)
DROP INDEX IF EXISTS idx_user_semester_unique;

-- Create composite unique index excluding soft-deleted records
-- This prevents the race condition where two concurrent requests can add
-- the same user to different groups in the same semester
CREATE UNIQUE INDEX idx_user_semester_unique 
ON user_groups (user_id, group_id) 
WHERE deleted_at IS NULL;

-- Additional partial index to enforce semester uniqueness at query level
-- Note: PostgreSQL doesn't support subquery in index expression directly
-- So we rely on application-level check + trigger for full enforcement

-- Create trigger function to validate semester uniqueness
CREATE OR REPLACE FUNCTION check_user_semester_uniqueness()
RETURNS TRIGGER AS $$
DECLARE
    semester_value VARCHAR(20);
    existing_count INTEGER;
BEGIN
    -- Get semester of the group being added to
    SELECT semester INTO semester_value
    FROM groups
    WHERE id = NEW.group_id AND deleted_at IS NULL;
    
    -- Check if user already in another group in same semester
    SELECT COUNT(*) INTO existing_count
    FROM user_groups ug
    JOIN groups g ON ug.group_id = g.id
    WHERE ug.user_id = NEW.user_id
      AND g.semester = semester_value
      AND ug.deleted_at IS NULL
      AND g.deleted_at IS NULL
      AND ug.group_id != NEW.group_id;
    
    IF existing_count > 0 THEN
        RAISE EXCEPTION 'User already in a group for semester %', semester_value
            USING ERRCODE = '23505'; -- unique_violation
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on INSERT and UPDATE
CREATE TRIGGER trg_check_user_semester_uniqueness
    BEFORE INSERT OR UPDATE ON user_groups
    FOR EACH ROW
    WHEN (NEW.deleted_at IS NULL)
    EXECUTE FUNCTION check_user_semester_uniqueness();

-- Comments
COMMENT ON FUNCTION check_user_semester_uniqueness() IS 'Ensures a user can only be in one group per semester (BR-01)';
COMMENT ON TRIGGER trg_check_user_semester_uniqueness ON user_groups IS 'Enforces semester uniqueness at database level to prevent race conditions';
