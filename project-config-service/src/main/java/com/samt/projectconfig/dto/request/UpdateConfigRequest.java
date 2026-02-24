package com.samt.projectconfig.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating project configuration.
 * All fields are optional (partial update supported).
 * 
 * BR-UPDATE-01: If any credentials updated → state transitions to DRAFT.
 */
public record UpdateConfigRequest(
    
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    @Pattern(
        regexp = "https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)(/.*)?",
        message = "Invalid Jira host URL format"
    )
    String jiraHostUrl,
    
    @Size(min = 100, max = 500, message = "Jira API token must be between 100-500 characters")
    @Pattern(
        regexp = "^ATATT[A-Za-z0-9+/=_-]{95,495}$",
        message = "Invalid Jira API token format"
    )
    String jiraApiToken,
    
    @Size(max = 512, message = "GitHub repository URL must not exceed 512 characters")
    @Pattern(
        regexp = "https://github\\.com/[\\w-]+/[\\w-]+",
        message = "Invalid GitHub repository URL"
    )
    String githubRepoUrl,
    
    @Size(min = 40, max = 255, message = "GitHub token must be between 40-255 characters")
    @Pattern(
        regexp = "^ghp_[A-Za-z0-9]{36,}$",
        message = "Invalid GitHub token format"
    )
    String githubToken
) {
    
    /**
     * Check if any credentials (tokens or URLs) were updated.
     * Used to determine if state should transition to DRAFT.
     */
    public boolean hasCredentialUpdates() {
        return jiraApiToken != null || githubToken != null 
            || jiraHostUrl != null || githubRepoUrl != null;
    }
}
