package com.example.syncservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a project configuration from Project Config Service.
 * Received via gRPC with decrypted tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfigDto {

    private Long configId;
    private Long groupId;
    private String jiraHostUrl;
    private String jiraApiToken;      // Decrypted
    private String jiraProjectKey;
    private String githubRepoUrl;
    private String githubAccessToken;  // Decrypted
    private String state;              // VERIFIED, DRAFT, INVALID
}
