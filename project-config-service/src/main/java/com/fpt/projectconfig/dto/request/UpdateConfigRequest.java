package com.fpt.projectconfig.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateConfigRequest {

    @Size(max = 255)
    @Pattern(regexp = "^https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com).*$",
            message = "Invalid Jira URL format")
    private String jiraHostUrl;

    @Pattern(regexp = "^ATATT[A-Za-z0-9+/=_-]{100,500}$",
            message = "Invalid Jira token format")
    private String jiraApiToken;

    @Size(max = 255)
    @Pattern(regexp = "^https://github\\.com/[\\w-]+/[\\w-]+$",
            message = "Invalid GitHub repository URL")
    private String githubRepoUrl;

    @Pattern(regexp = "^ghp_[A-Za-z0-9]{36,}$",
            message = "Invalid GitHub token format")
    private String githubToken;

    public boolean hasCriticalFieldUpdate() {
        return jiraHostUrl != null || jiraApiToken != null ||
                githubRepoUrl != null || githubToken != null;
    }
}
