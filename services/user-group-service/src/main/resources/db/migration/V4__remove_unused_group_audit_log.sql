-- ============================================
-- USER-GROUP SERVICE - FLYWAY MIGRATION
-- ============================================
-- Version: V4__remove_unused_group_audit_log.sql
-- Description: Remove unused group_audit_log table
-- Reason: Audit logging is handled via application logs (SLF4J), not database.
--         This table was created in V1 but never used by the application.

-- ============================================
-- DROP: group_audit_log (unused table)
-- ============================================
DROP TABLE IF EXISTS group_audit_log;

COMMENT ON SCHEMA public IS 'Audit logging is application-level only (SLF4J). No database audit trail exists.';
