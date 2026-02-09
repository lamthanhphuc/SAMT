package com.fpt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for project config
 * Tokens are masked based on user role (see TokenMaskingService)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigResponse {

    private UUID id;
    private UUID groupId;
    private String jiraHostUrl;
    private String jiraApiToken; // Masked for STUDENT, full for ADMIN/LECTURER
    private String githubRepoUrl;
    private String githubToken; // Masked for STUDENT, full for ADMIN/LECTURER
    private String state;
    private Instant lastVerifiedAt;
    private String invalidReason;
    private Instant createdAt;
    private Instant updatedAt;
}
