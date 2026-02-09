package com.fpt.projectconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateConfigRequest {

    @NotNull(message = "Group ID is required")
    private UUID groupId;

    @NotBlank(message = "Jira host URL is required")
    @Size(max = 255)
    @Pattern(regexp = "^https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com).*$",
            message = "Invalid Jira URL format")
    private String jiraHostUrl;

    @NotBlank(message = "Jira API token is required")
    @Pattern(regexp = "^ATATT[A-Za-z0-9+/=_-]{100,500}$",
            message = "Invalid Jira token format")
    private String jiraApiToken;

    @NotBlank(message = "GitHub repository URL is required")
    @Size(max = 255)
    @Pattern(regexp = "^https://github\\.com/[\\w-]+/[\\w-]+$",
            message = "Invalid GitHub repository URL")
    private String githubRepoUrl;

    @NotBlank(message = "GitHub token is required")
    @Pattern(regexp = "^ghp_[A-Za-z0-9]{36,}$",
            message = "Invalid GitHub token format")
    private String githubToken;
}
