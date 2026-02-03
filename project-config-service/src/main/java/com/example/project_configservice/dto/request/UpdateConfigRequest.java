package com.example.project_configservice.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing project configuration.
 * All fields are optional (partial update supported).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigRequest {
    
    @Pattern(
        regexp = "https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)",
        message = "Invalid Jira host URL format"
    )
    @Size(max = 255, message = "Jira host URL must not exceed 255 characters")
    private String jiraHostUrl;
    
    @Pattern(
        regexp = "^ATATT[A-Za-z0-9+/=_-]{100,500}$",
        message = "Invalid Jira API token format"
    )
    private String jiraApiToken;
    
    @Pattern(
        regexp = "https://github\\.com/[\\w-]+/[\\w-]+",
        message = "Invalid GitHub repository URL format"
    )
    @Size(max = 255, message = "GitHub repository URL must not exceed 255 characters")
    private String githubRepoUrl;
    
    @Pattern(
        regexp = "^ghp_[A-Za-z0-9]{36,}$",
        message = "Invalid GitHub token format"
    )
    @Size(min = 40, max = 255, message = "GitHub token must be between 40 and 255 characters")
    private String githubToken;
    
    /**
     * Check if any credential field was updated.
     * If true, config state should transition to DRAFT.
     */
    public boolean hasCredentialUpdates() {
        return jiraApiToken != null || githubToken != null || 
               jiraHostUrl != null || githubRepoUrl != null;
    }
}
