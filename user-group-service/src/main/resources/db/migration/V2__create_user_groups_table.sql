-- V2__create_user_groups_table.sql
-- Create user_groups table for many-to-many relationship between users and groups
-- user_id has NO FK constraint (cross-service reference to Identity Service)
-- group_id has FK constraint to groups table

CREATE TABLE user_groups (
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('LEADER', 'MEMBER')),
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Composite primary key
    PRIMARY KEY (user_id, group_id),
    
    -- FK to groups table
    CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) 
        REFERENCES groups(id) ON DELETE RESTRICT
);

-- Unique partial index: only one LEADER per group (excluding soft-deleted)
CREATE UNIQUE INDEX uq_group_leader 
ON user_groups(group_id) 
WHERE role = 'LEADER' AND deleted_at IS NULL;

-- Indexes
CREATE INDEX idx_user_groups_user_id ON user_groups(user_id);
CREATE INDEX idx_user_groups_group_id ON user_groups(group_id);
CREATE INDEX idx_user_groups_role ON user_groups(role);
CREATE INDEX idx_user_groups_deleted_at ON user_groups(deleted_at);

-- Comments
COMMENT ON TABLE user_groups IS 'Many-to-many relationship between users and groups with role';
COMMENT ON COLUMN user_groups.user_id IS 'UUID reference to Identity Service (NO FK constraint)';
COMMENT ON COLUMN user_groups.group_id IS 'FK to groups table';
COMMENT ON COLUMN user_groups.role IS 'Group role: LEADER or MEMBER (different from system role)';
COMMENT ON COLUMN user_groups.deleted_at IS 'Soft delete timestamp - NULL means active membership';
