package com.samt.projectconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating new project configuration.
 * All fields are required.
 */
public record CreateConfigRequest(
    
    @NotNull(message = "Group ID is required")
    Long groupId,
    
    @NotBlank(message = "Jira host URL is required")
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    @Pattern(
        regexp = "https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)(/.*)?",
        message = "Invalid Jira host URL format. Must be https://domain.atlassian.net or custom Jira domain"
    )
    String jiraHostUrl,
    
    @NotBlank(message = "Jira API token is required")
    @Size(min = 100, max = 500, message = "Jira API token must be between 100-500 characters")
    @Pattern(
        regexp = "^ATATT[A-Za-z0-9+/=_-]{95,495}$",
        message = "Invalid Jira API token format. Must start with 'ATATT'"
    )
    String jiraApiToken,
    
    @NotBlank(message = "GitHub repository URL is required")
    @Size(max = 512, message = "GitHub repository URL must not exceed 512 characters")
    @Pattern(
        regexp = "https://github\\.com/[\\w-]+/[\\w-]+",
        message = "Invalid GitHub repository URL. Must be https://github.com/owner/repo"
    )
    String githubRepoUrl,
    
    @NotBlank(message = "GitHub token is required")
    @Size(min = 40, max = 255, message = "GitHub token must be between 40-255 characters")
    @Pattern(
        regexp = "^ghp_[A-Za-z0-9]{36,}$",
        message = "Invalid GitHub token format. Must start with 'ghp_'"
    )
    String githubToken
) {}
