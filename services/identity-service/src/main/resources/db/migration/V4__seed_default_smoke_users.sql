-- =====================================================
-- V4: Seed default smoke-test users (idempotent)
-- Identity Service - SAMT Project
-- =====================================================

-- Reuse student1 password hash so admin/lecturer can use Str0ng@Pass! in smoke tests.
WITH student_hash AS (
    SELECT password_hash
    FROM users
    WHERE email = 'student1@samt.local'
    LIMIT 1
)
INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'admin@samt.local', password_hash, 'System Admin', 'ADMIN', 'ACTIVE'
FROM student_hash
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@samt.local');

WITH student_hash AS (
    SELECT password_hash
    FROM users
    WHERE email = 'student1@samt.local'
    LIMIT 1
)
INSERT INTO users (email, password_hash, full_name, role, status)
SELECT 'lecturer1@samt.local', password_hash, 'Lecturer One', 'LECTURER', 'ACTIVE'
FROM student_hash
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'lecturer1@samt.local');
