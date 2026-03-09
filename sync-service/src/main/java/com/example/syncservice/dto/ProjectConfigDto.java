package com.example.syncservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO representing a project configuration from Project Config Service.
 * Received via gRPC with decrypted tokens.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfigDto {

    private UUID configId;
    private Long groupId;
    private String jiraHostUrl;
    private String jiraEmail;
    private String jiraApiToken;      // Decrypted
    private String githubRepoUrl;
    private String githubToken;        // Decrypted
    private String state;              // VERIFIED, DRAFT, INVALID
}
