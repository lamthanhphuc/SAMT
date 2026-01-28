-- =====================================================
-- V2: Add Soft Delete and Audit Log
-- Identity Service - SAMT Project
-- =====================================================

-- 1. Add soft delete columns to users table
ALTER TABLE users
ADD COLUMN deleted_at TIMESTAMP NULL,
ADD COLUMN deleted_by BIGINT NULL;

-- Index for soft delete queries (filter deleted users)
CREATE INDEX idx_users_deleted_at ON users(deleted_at);

-- 2. Create audit_logs table
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    
    -- What was changed
    entity_type VARCHAR(100) NOT NULL,      -- 'USER', 'REFRESH_TOKEN'
    entity_id BIGINT NOT NULL,              -- ID of the entity
    
    -- What action was performed
    action VARCHAR(50) NOT NULL,            -- CREATE, UPDATE, DELETE, RESTORE, LOGIN, LOGOUT, etc.
    
    -- Who performed the action
    actor_id BIGINT NULL,                   -- User ID from JWT (NULL for system actions)
    actor_email VARCHAR(255) NULL,          -- Email for readability
    
    -- When
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Additional context
    ip_address VARCHAR(45) NULL,            -- Client IP (IPv6 compatible)
    user_agent VARCHAR(500) NULL,           -- Browser/client info
    
    -- Change details (JSON format)
    old_value TEXT NULL,                    -- Previous state (JSON)
    new_value TEXT NULL,                    -- New state (JSON)
    
    -- Outcome
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'  -- SUCCESS, FAILURE, DENIED
);

-- Indexes for audit queries
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_outcome ON audit_logs(outcome);

-- 3. Add comment for documentation
COMMENT ON TABLE audit_logs IS 'Audit trail for security-sensitive operations';
COMMENT ON COLUMN audit_logs.action IS 'Action types: CREATE, UPDATE, DELETE, RESTORE, LOGIN, LOGIN_FAILED, LOGOUT, REFRESH, REFRESH_REUSE';
COMMENT ON COLUMN audit_logs.outcome IS 'Outcome types: SUCCESS, FAILURE, DENIED';
