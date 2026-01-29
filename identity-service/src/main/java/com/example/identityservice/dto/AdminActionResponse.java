package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for admin actions (soft delete, restore, lock, unlock).
 * @see docs/SRS-Auth.md - Admin API Endpoints
 */
public record AdminActionResponse(
    @JsonProperty("message")
    String message,

    @JsonProperty("userId")
    String userId
) {
    public static AdminActionResponse of(String message, Long userId) {
        return new AdminActionResponse(message, String.valueOf(userId));
    }
}
