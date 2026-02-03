package com.example.project_configservice.dto.response;

import com.example.project_configservice.entity.ConfigState;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for project configuration (with masked tokens).
 * Used for public API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigResponse {
    
    private UUID id;
    private UUID groupId;
    
    // URLs (not masked)
    private String jiraHostUrl;
    private String githubRepoUrl;
    
    // Tokens (masked: show first 3 + last 4 chars)
    private String jiraApiToken;
    private String githubToken;
    
    // State machine
    private ConfigState state;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant lastVerifiedAt;
    
    private String invalidReason;
    
    // Audit timestamps
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant updatedAt;
}
