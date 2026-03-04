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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String email;
    private String role;
    private Instant deletedAt;
    private Long deletedBy;
    private String eventId;
    private Instant eventTimestamp;
}
