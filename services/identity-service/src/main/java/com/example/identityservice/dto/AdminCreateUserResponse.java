package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for admin user creation.
 */
public record AdminCreateUserResponse(
    @JsonProperty("message")
    String message,

    @JsonProperty("user")
    UserDto user,

    @JsonProperty("temporaryPassword")
    String temporaryPassword
) {
    public static AdminCreateUserResponse of(String message, UserDto user, String temporaryPassword) {
        return new AdminCreateUserResponse(message, user, temporaryPassword);
    }
}
