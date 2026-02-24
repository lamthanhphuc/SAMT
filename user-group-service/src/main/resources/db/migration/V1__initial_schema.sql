-- ============================================
-- USER-GROUP SERVICE - FLYWAY MIGRATION
-- ============================================
-- Version: V1__initial_schema.sql
-- Description: Tạo schema ban đầu cho User-Group Service
-- - Không sử dụng Foreign Key xuyên database (user_id, deleted_by là logical reference)
-- - Unique constraint: một user chỉ thuộc một group trong một semester

-- ============================================
-- TABLE: semesters
-- ============================================
CREATE TABLE semesters (
    id BIGSERIAL PRIMARY KEY,
    semester_code VARCHAR(50) NOT NULL UNIQUE,
    semester_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_semesters_code ON semesters(semester_code);
CREATE INDEX idx_semesters_active ON semesters(is_active);

COMMENT ON TABLE semesters IS 'Học kỳ (Semester)';
COMMENT ON COLUMN semesters.semester_code IS 'Mã học kỳ (e.g., FALL2024, SPRING2025)';
COMMENT ON COLUMN semesters.is_active IS 'Học kỳ hiện tại đang active';

-- ============================================
-- TABLE: groups
-- ============================================
CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    semester_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,  -- Logical reference to Identity Service users.id (NO FK CONSTRAINT)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service users.id (NO FK CONSTRAINT)
    
    CONSTRAINT fk_groups_semester FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE
);

-- Unique constraint: group_name phải unique trong một semester (soft delete aware)
CREATE UNIQUE INDEX idx_groups_name_semester_unique 
    ON groups(group_name, semester_id) 
    WHERE deleted_at IS NULL;

CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_semester_id ON groups(semester_id);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);

COMMENT ON TABLE groups IS 'Nhóm dự án sinh viên';
COMMENT ON COLUMN groups.lecturer_id IS 'ID Giảng viên phụ trách (logical reference, NO FK)';
COMMENT ON COLUMN groups.deleted_by IS 'User ID thực hiện soft delete (logical reference, NO FK)';

-- ============================================
-- TABLE: user_semester_membership
-- ============================================
-- Business Rule: Một user chỉ thuộc MỘT group trong MỘT semester
CREATE TABLE user_semester_membership (
    user_id BIGINT NOT NULL,  -- Logical reference to Identity Service users.id (NO FK CONSTRAINT)
    semester_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_role VARCHAR(20) NOT NULL CHECK (group_role IN ('LEADER', 'MEMBER')),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service users.id (NO FK CONSTRAINT)
    
    PRIMARY KEY (user_id, semester_id),
    CONSTRAINT fk_usm_semester FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
    CONSTRAINT fk_usm_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

-- Unique constraint: Chỉ có 1 LEADER trong một group
CREATE UNIQUE INDEX idx_usm_group_leader_unique 
    ON user_semester_membership(group_id) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;

CREATE INDEX idx_usm_user_id ON user_semester_membership(user_id);
CREATE INDEX idx_usm_group_id ON user_semester_membership(group_id);
CREATE INDEX idx_usm_semester_id ON user_semester_membership(semester_id);
CREATE INDEX idx_usm_deleted_at ON user_semester_membership(deleted_at);

COMMENT ON TABLE user_semester_membership IS 'Membership của user trong group theo semester';
COMMENT ON COLUMN user_semester_membership.user_id IS 'User ID (logical reference, NO FK)';
COMMENT ON COLUMN user_semester_membership.group_role IS 'Vai trò trong nhóm: LEADER hoặc MEMBER';
COMMENT ON CONSTRAINT idx_usm_group_leader_unique ON user_semester_membership IS 'Enforce: Chỉ 1 LEADER per group';

-- ============================================
-- AUDIT LOG TABLE (Optional) - REMOVED IN V4
-- ============================================
-- NOTE: This table was created in V1 but never used by the application.
-- Removed in V4__remove_unused_group_audit_log.sql
-- Audit logging is handled via application logs (SLF4J) only.
CREATE TABLE group_audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,  -- 'GROUP', 'USER_SEMESTER_MEMBERSHIP'
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,  -- 'CREATE', 'UPDATE', 'DELETE', 'RESTORE'
    performed_by BIGINT NOT NULL,  -- Logical reference to Identity Service users.id (NO FK)
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details JSONB NULL
);

CREATE INDEX idx_audit_entity ON group_audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_performed_at ON group_audit_log(performed_at);

COMMENT ON TABLE group_audit_log IS 'Audit trail cho tất cả thao tác với groups và memberships';

-- ============================================
-- SAMPLE DATA (For Development)
-- ============================================
-- Insert sample semester
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, is_active)
VALUES 
    ('SPRING2025', 'Spring Semester 2025', '2025-01-01', '2025-05-31', TRUE),
    ('FALL2024', 'Fall Semester 2024', '2024-09-01', '2024-12-31', FALSE);
