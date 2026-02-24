package com.samt.projectconfig.dto.response;

import lombok.Builder;

import java.util.UUID;

/**
 * Response DTO for internal API (Sync Service).
 * Returns DECRYPTED tokens (no masking).
 * 
 * SEC-INTERNAL-03: Only for service-to-service authentication.
 */
@Builder
public record DecryptedTokensResponse(
    UUID configId,
    Long groupId,
    String jiraHostUrl,
    String jiraApiToken,  // DECRYPTED raw token
    String githubRepoUrl,
    String githubToken,   // DECRYPTED raw token
    String state
) {}
