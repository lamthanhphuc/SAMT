-- V1__create_users_table.sql
-- Create users table for User & Group Service

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_deleted_at ON users(deleted_at);

-- Comments
COMMENT ON TABLE users IS 'User profiles for the User & Group Service';
COMMENT ON COLUMN users.id IS 'Primary key - same as identity-service user ID';
COMMENT ON COLUMN users.status IS 'User status: ACTIVE or INACTIVE';
COMMENT ON COLUMN users.deleted_at IS 'Soft delete timestamp - NULL means active';
