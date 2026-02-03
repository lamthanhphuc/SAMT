package com.example.project_configservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a new project configuration.
 * All fields are required.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConfigRequest {
    
    @NotNull(message = "Group ID is required")
    private UUID groupId;
    
    @NotNull(message = "Jira host URL is required")
    @Pattern(
        regexp = "https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)",
        message = "Invalid Jira host URL format"
    )
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    private String jiraHostUrl;
    
    @NotNull(message = "Jira API token is required")
    @Pattern(
        regexp = "^ATATT[A-Za-z0-9+/=_-]{100,500}$",
        message = "Invalid Jira API token format"
    )
    private String jiraApiToken;
    
    @NotNull(message = "GitHub repository URL is required")
    @Pattern(
        regexp = "https://github\\.com/[\\w-]+/[\\w-]+",
        message = "Invalid GitHub repository URL format"
    )
    @Size(max = 255, message = "GitHub repository URL must not exceed 255 characters")
    private String githubRepoUrl;
    
    @NotNull(message = "GitHub token is required")
    @Pattern(
        regexp = "^ghp_[A-Za-z0-9]{36,}$",
        message = "Invalid GitHub token format"
    )
    @Size(min = 40, max = 255, message = "GitHub token must be between 40 and 255 characters")
    private String githubToken;
}
