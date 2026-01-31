-- V4__alter_ids_to_bigint.sql
-- Convert lecturer_id and user_id from UUID to BIGINT
-- This is a breaking change - existing data will be lost

-- Drop existing foreign keys and constraints
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS fk_user_groups_group_id;

-- Truncate tables to clear existing data (development only)
TRUNCATE TABLE user_groups CASCADE;
TRUNCATE TABLE groups CASCADE;

-- Alter groups.lecturer_id from UUID to BIGINT
ALTER TABLE groups 
    ALTER COLUMN lecturer_id TYPE BIGINT USING NULL;

-- Alter user_groups.user_id from UUID to BIGINT  
ALTER TABLE user_groups
    ALTER COLUMN user_id TYPE BIGINT USING NULL;

-- Recreate foreign key constraint
ALTER TABLE user_groups
    ADD CONSTRAINT fk_user_groups_group_id 
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE;

-- Update comments
COMMENT ON COLUMN groups.lecturer_id IS 'BIGINT reference to Identity Service lecturer (NO FK constraint)';
COMMENT ON COLUMN user_groups.user_id IS 'BIGINT reference to Identity Service user (NO FK constraint)';
