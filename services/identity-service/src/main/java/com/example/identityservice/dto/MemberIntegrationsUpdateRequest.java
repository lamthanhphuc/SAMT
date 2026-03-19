package com.example.identityservice.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for updating a member's integration identifiers.
 *
 * Rules (per spec):
 * - Only non-null fields are updated (PATCH-like semantics).
 * - jiraAccountId: not blank, length > 5
 * - githubUsername: ^[a-zA-Z0-9-]{1,39}$
 */
public record MemberIntegrationsUpdateRequest(
    @Pattern(
        // Jira Cloud accountId commonly looks like "<number>:<uuid>" where uuid contains hyphens.
        regexp = "^[a-zA-Z0-9:-]{5,100}$",
        message = "jiraAccountId must be at least 5 characters and contain only letters, numbers, ':', or '-'"
    )
    String jiraAccountId,

    @Pattern(
        regexp = "^[a-zA-Z0-9-]{1,39}$",
        message = "githubUsername must match ^[a-zA-Z0-9-]{1,39}$"
    )
    String githubUsername
) {
}

