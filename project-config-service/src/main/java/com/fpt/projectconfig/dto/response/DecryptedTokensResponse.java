package com.fpt.projectconfig.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for decrypted tokens (Internal API only)
 * Used by Sync Service to get full credentials
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecryptedTokensResponse {

    private UUID configId;
    private UUID groupId;
    private String jiraHostUrl;
    private String jiraApiToken; // Decrypted, full token
    private String githubRepoUrl;
    private String githubToken; // Decrypted, full token
    private String state;
}
