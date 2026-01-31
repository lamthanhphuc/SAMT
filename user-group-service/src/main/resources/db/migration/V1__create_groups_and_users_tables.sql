-- V1__create_groups_and_users_tables.sql
-- Create groups table for student groups
-- lecturer_id has NO FK constraint (cross-service reference to Identity Service)

CREATE TABLE groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_name VARCHAR(50) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    lecturer_id UUID NOT NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: group name must be unique within semester
    CONSTRAINT uq_group_semester UNIQUE (group_name, semester)
);

-- Indexes
CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_semester ON groups(semester);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);

-- Comments
COMMENT ON TABLE groups IS 'Student groups for project/course assignments';
COMMENT ON COLUMN groups.lecturer_id IS 'UUID reference to Identity Service lecturer (NO FK constraint)';
COMMENT ON COLUMN groups.deleted_at IS 'Soft delete timestamp - NULL means active group';
