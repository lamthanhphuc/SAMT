-- =====================================================
-- V5: Seed smoke-test users for clean databases (idempotent)
-- Identity Service - SAMT Project
-- =====================================================

-- Password for all seeded accounts: Str0ng@Pass!
INSERT INTO users (email, password_hash, full_name, role, status)
SELECT
    seed.email,
    '$2a$10$zvAX7YvBuDfh/WRyWbRDROcfEphFaX1lKukr5g.ugcJfQZeE5gQCi',
    seed.full_name,
    seed.role,
    'ACTIVE'
FROM (
    VALUES
        ('student1@samt.local', 'Student One', 'STUDENT'),
        ('admin@samt.local', 'System Admin', 'ADMIN'),
        ('lecturer1@samt.local', 'Lecturer One', 'LECTURER')
) AS seed(email, full_name, role)
WHERE NOT EXISTS (
    SELECT 1
    FROM users existing
    WHERE existing.email = seed.email
);