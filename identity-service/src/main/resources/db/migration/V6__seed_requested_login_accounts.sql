-- =====================================================
-- V6: Seed requested login accounts for gateway verification
-- Identity Service - SAMT Project
-- =====================================================

INSERT INTO users (email, password_hash, full_name, role, status, created_at, updated_at)
VALUES
    ('Admin@gmail.com', '$2a$10$V/JVqbngZkOg5RnLY0tSwuXgFvqMVrmmyDL7G4mo6PnlZhqHUOvjq', 'Admin User', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('Lecturer@gmail.com', '$2a$10$V/JVqbngZkOg5RnLY0tSwuXgFvqMVrmmyDL7G4mo6PnlZhqHUOvjq', 'Lecturer User', 'LECTURER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('phucltse184678@fpt.edu.vn', '$2a$10$V/JVqbngZkOg5RnLY0tSwuXgFvqMVrmmyDL7G4mo6PnlZhqHUOvjq', 'Student User', 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO UPDATE
SET
    password_hash = EXCLUDED.password_hash,
    full_name = EXCLUDED.full_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;