package com.samt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for project configuration.
 * Tokens are MASKED for security (never return raw tokens).
 */
@Builder
public record ConfigResponse(
    UUID id,
    Long groupId,
    String jiraHostUrl,
    String jiraApiToken,  // MASKED: "***ab12"
    String githubRepoUrl,
    String githubToken,   // MASKED: "ghp_***xyz9"
    String state,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant lastVerifiedAt,
    
    String invalidReason,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant createdAt,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant updatedAt
) {}
