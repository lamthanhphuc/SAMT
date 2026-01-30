package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for external accounts mapping operation.
 * 
 * @see docs/API_CONTRACT.md - PUT /api/admin/users/{userId}/external-accounts
 */
public record ExternalAccountsResponse(
    @JsonProperty("message")
    String message,

    @JsonProperty("user")
    UserDto user
) {
    public static ExternalAccountsResponse of(String message, UserDto user) {
        return new ExternalAccountsResponse(message, user);
    }
}
