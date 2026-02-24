-- ============================================
-- USER-GROUP SERVICE - FLYWAY MIGRATION
-- ============================================
-- Version: V2__add_user_group_constraints.sql
-- Description: Thêm constraints và indexes để tối ưu performance

-- ============================================
-- ADDITIONAL INDEXES FOR PERFORMANCE
-- ============================================

-- Index cho query "get all groups of a user"
CREATE INDEX idx_usm_user_semester_active 
    ON user_semester_membership(user_id, semester_id) 
    WHERE deleted_at IS NULL;

-- Index cho query "get all members of a group"
CREATE INDEX idx_usm_group_active 
    ON user_semester_membership(group_id) 
    WHERE deleted_at IS NULL;

-- Index cho query "check if user is leader of any group"
CREATE INDEX idx_usm_user_leader 
    ON user_semester_membership(user_id, group_role) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;

-- ============================================
-- FUNCTION: Prevent duplicate membership
-- ============================================
-- Business Rule: User không thể join nhiều group trong cùng một semester

CREATE OR REPLACE FUNCTION check_user_semester_uniqueness()
RETURNS TRIGGER AS $$
BEGIN
    -- Check if user already has active membership in this semester
    IF EXISTS (
        SELECT 1 
        FROM user_semester_membership 
        WHERE user_id = NEW.user_id 
        AND semester_id = NEW.semester_id 
        AND deleted_at IS NULL
        AND (user_id != OLD.user_id OR semester_id != OLD.semester_id)  -- Allow updates
    ) THEN
        RAISE EXCEPTION 'User % already belongs to a group in semester %', 
            NEW.user_id, NEW.semester_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_user_semester_uniqueness
    BEFORE INSERT OR UPDATE ON user_semester_membership
    FOR EACH ROW
    EXECUTE FUNCTION check_user_semester_uniqueness();

COMMENT ON FUNCTION check_user_semester_uniqueness IS 'Enforce: User chỉ được thuộc 1 group trong 1 semester';

-- ============================================
-- FUNCTION: Prevent group without leader
-- ============================================
-- Business Rule: Mỗi group phải có ít nhất 1 LEADER

CREATE OR REPLACE FUNCTION check_group_has_leader()
RETURNS TRIGGER AS $$
BEGIN
    -- When deleting or changing role from LEADER
    IF (TG_OP = 'DELETE' OR (TG_OP = 'UPDATE' AND OLD.group_role = 'LEADER')) THEN
        IF NOT EXISTS (
            SELECT 1 
            FROM user_semester_membership 
            WHERE group_id = OLD.group_id 
            AND group_role = 'LEADER' 
            AND deleted_at IS NULL
            AND user_id != OLD.user_id  -- Exclude current record
        ) THEN
            RAISE EXCEPTION 'Cannot remove last leader from group %', OLD.group_id;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_group_has_leader
    BEFORE UPDATE OR DELETE ON user_semester_membership
    FOR EACH ROW
    EXECUTE FUNCTION check_group_has_leader();

COMMENT ON FUNCTION check_group_has_leader IS 'Enforce: Group phải có ít nhất 1 LEADER';
