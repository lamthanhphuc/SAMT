-- V2__create_groups_table.sql
-- Create groups table for Group management

CREATE TABLE groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name VARCHAR(50) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    lecturer_id UUID NOT NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key to users table (lecturer)
    CONSTRAINT fk_groups_lecturer FOREIGN KEY (lecturer_id) REFERENCES users(id),
    
    -- Unique constraint: group_name + semester
    CONSTRAINT uq_group_semester UNIQUE (group_name, semester)
);

-- Indexes
CREATE INDEX idx_groups_semester ON groups(semester);
CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);

-- Comments
COMMENT ON TABLE groups IS 'Student groups for each semester';
COMMENT ON COLUMN groups.group_name IS 'Group name format: SE1705-G1';
COMMENT ON COLUMN groups.semester IS 'Semester format: Spring2026, Fall2025';
COMMENT ON COLUMN groups.lecturer_id IS 'FK to users - the lecturer assigned to this group';
COMMENT ON COLUMN groups.deleted_at IS 'Soft delete timestamp - NULL means active';
