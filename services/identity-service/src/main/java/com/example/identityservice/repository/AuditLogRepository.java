package com.example.identityservice.repository;

import com.example.identityservice.entity.AuditAction;
import com.example.identityservice.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity.
 * 
 * Design Decision:
 * - Read-only queries (no update/delete methods)
 * - Paginated queries for large datasets
 * - Indexed queries for common access patterns
 * 
 * @see docs/Database-Design.md - Section 6. Table: audit_logs
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find audit logs by entity type and ID.
     * Use case: View history of a specific user.
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, Long entityId);

    /**
     * Find audit logs by entity type and ID (paginated).
     * Use case: Admin view history of a specific entity.
     */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, Long entityId, Pageable pageable);

    /**
     * Find audit logs by actor (who performed the action).
     * Use case: View all actions by a specific admin.
     */
    Page<AuditLog> findByActorIdOrderByTimestampDesc(Long actorId, Pageable pageable);

    /**
     * Find audit logs by action type.
     * Use case: View all login failures.
     */
    Page<AuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    /**
     * Find audit logs within a time range.
     * Use case: Daily/weekly security reports.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    /**
     * Find security events (failed logins, token reuse).
     * Use case: Security monitoring dashboard.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.outcome IN ('FAILURE', 'DENIED') ORDER BY a.timestamp DESC")
    Page<AuditLog> findSecurityEvents(Pageable pageable);

    /**
     * Count actions by type within time range.
     * Use case: Analytics - login count per day.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action AND a.timestamp BETWEEN :start AND :end")
    long countByActionAndDateRange(
            @Param("action") AuditAction action,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
