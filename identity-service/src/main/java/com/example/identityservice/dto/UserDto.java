package com.example.identityservice.dto;

import com.example.identityservice.entity.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * User DTO for API responses.
 * @see docs/SRS.md - UC-REGISTER Response.user
 */
public record UserDto(
    @JsonProperty("id")
    Long id,

    @JsonProperty("email")
    String email,

    @JsonProperty("fullName")
    String fullName,

    @JsonProperty("role")
    String role,

    @JsonProperty("status")
    String status,

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    LocalDateTime createdAt
) {
    /**
     * Factory method to create UserDto from User entity.
     */
    public static UserDto fromEntity(User user) {
        return new UserDto(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getCreatedAt()
        );
    }
}
