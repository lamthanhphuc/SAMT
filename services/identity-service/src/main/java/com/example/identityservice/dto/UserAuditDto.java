package com.example.identityservice.dto;

import com.example.identityservice.entity.User;

import java.time.LocalDateTime;

/**
 * DTO for audit logging - excludes sensitive fields like passwordHash.
 * 
 * Security: This DTO is used in audit logs to prevent password hash leakage.
 * Never serialize User entity directly to audit logs.
 * 
 * @see com.example.identityservice.service.AuditService
 */
public record UserAuditDto(
        Long id,
        String email,
        String fullName,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        Long deletedBy
) {
    /**
     * Create UserAuditDto from User entity.
     * Excludes passwordHash for security.
     * 
     * @param user User entity
     * @return UserAuditDto without sensitive fields
     */
    public static UserAuditDto from(User user) {
        return new UserAuditDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDeletedAt(),
                user.getDeletedBy()
        );
    }

    /**
     * Create a minimal audit representation (for before-state capture).
     * 
     * @param user User entity
     * @return Minimal UserAuditDto
     */
    public static UserAuditDto minimal(User user) {
        return new UserAuditDto(
                user.getId(),
                user.getEmail(),
                null, // fullName not needed for minimal
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                null, null, null, null
        );
    }
}
