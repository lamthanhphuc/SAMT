package com.example.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * User Deleted Event
 * 
 * Published by: Identity Service khi user bị hard delete
 * Consumed by: 
 * - User-Group Service: Remove user từ tất cả groups
 * - Project Config Service: Remove/reassign project configs
 * - Sync Service: Clean up sync history
 * 
 * Event Flow:
 * 1. Admin hard delete user trong Identity Service
 * 2. Identity Service publish UserDeletedEvent
 * 3. Các services khác consume event và cleanup data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /**
     * User ID bị delete
     */
    private Long userId;

    /**
     * Email của user (for logging)
     */
    private String email;

    /**
     * Role của user (for logging)
     */
    private String role;

    /**
     * Timestamp khi user bị delete
     */
    private Instant deletedAt;

    /**
     * Admin ID thực hiện delete (for audit)
     */
    private Long deletedBy;

    /**
     * Event ID (UUID) để deduplicate
     */
    private String eventId;

    /**
     * Event timestamp
     */
    private Instant eventTimestamp;
}
