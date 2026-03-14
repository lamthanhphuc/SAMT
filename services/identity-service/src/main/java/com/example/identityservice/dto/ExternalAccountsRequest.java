package com.example.identityservice.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for mapping/unmapping external accounts (Jira, GitHub).
 * 
 * @see docs/API_CONTRACT.md - PUT /api/admin/users/{userId}/external-accounts
 * 
 * Validation Rules:
 * - jiraAccountId: alphanumeric, 20-30 chars (null to unmap)
 * - githubUsername: alphanumeric + hyphen, 1-39 chars (null to unmap)
 */
public record ExternalAccountsRequest(
    @Pattern(
        regexp = "^[a-zA-Z0-9]{20,30}$",
        message = "Jira account ID must be alphanumeric, 20-30 characters"
    )
    String jiraAccountId,

    @Pattern(
        regexp = "^[a-zA-Z0-9-]{1,39}$",
        message = "GitHub username must be alphanumeric with hyphens, 1-39 characters"
    )
    String githubUsername
) {
}
