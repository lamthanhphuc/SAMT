-- ============================================
-- USER-GROUP SERVICE - FLYWAY MIGRATION V3
-- ============================================
-- Version: V3__fix_leader_trigger.sql
-- Description: Fix leader removal trigger to allow removing last leader when group is empty
-- 
-- CRITICAL-3 FIX:
-- Previous trigger blocked removing last leader in ALL cases
-- New logic: Only block removing last leader if OTHER members exist
-- Allow removing last leader if group is empty (for cleanup scenarios)

-- ============================================
-- Drop old trigger and function
-- ============================================
DROP TRIGGER IF EXISTS trg_check_group_has_leader ON user_semester_membership;
DROP FUNCTION IF EXISTS check_group_has_leader();

-- ============================================
-- Create new function with correct business logic
-- ============================================
CREATE OR REPLACE FUNCTION check_group_has_leader()
RETURNS TRIGGER AS $$
DECLARE
    other_members_count INT;
    other_leaders_count INT;
BEGIN
    -- Only check when deleting or demoting a LEADER
    IF (TG_OP = 'DELETE' AND OLD.group_role = 'LEADER') OR 
       (TG_OP = 'UPDATE' AND OLD.group_role = 'LEADER' AND NEW.group_role != 'LEADER') THEN
        
        -- Count OTHER active members (excluding current record being deleted/demoted)
        SELECT COUNT(*) INTO other_members_count
        FROM user_semester_membership
        WHERE group_id = OLD.group_id
        AND deleted_at IS NULL
        AND user_id != OLD.user_id;
        
        -- Business Rule Logic:
        -- If there are other members, ensure at least one other leader exists
        -- If no other members (group is empty), allow removing last leader
        IF other_members_count > 0 THEN
            -- Count other leaders (excluding current record)
            SELECT COUNT(*) INTO other_leaders_count
            FROM user_semester_membership
            WHERE group_id = OLD.group_id
            AND group_role = 'LEADER'
            AND deleted_at IS NULL
            AND user_id != OLD.user_id;
            
            -- Block if no other leader exists when members remain
            IF other_leaders_count = 0 THEN
                RAISE EXCEPTION 'Cannot remove last leader when group has other members (group_id: %, other_members: %)', 
                    OLD.group_id, other_members_count;
            END IF;
        END IF;
        -- If other_members_count = 0, allow operation (group cleanup scenario)
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Create trigger
-- ============================================
CREATE TRIGGER trg_check_group_has_leader
    BEFORE UPDATE OR DELETE ON user_semester_membership
    FOR EACH ROW
    EXECUTE FUNCTION check_group_has_leader();

-- ============================================
-- Comments for documentation
-- ============================================
COMMENT ON FUNCTION check_group_has_leader IS 
'Enforce business rule: Groups with multiple members MUST have at least 1 LEADER. 
Allows removing last leader if group is empty (for group cleanup).
Updated in V3 to fix CRITICAL-3 production blocker.';

COMMENT ON TRIGGER trg_check_group_has_leader ON user_semester_membership IS 
'Prevents removing last leader when other members exist. 
Allows removing last leader when group is empty.';
