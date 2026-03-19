package com.example.identityservice.dto;

import com.example.identityservice.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberIntegrationsResponse(
    @JsonProperty("id") Long id,
    @JsonProperty("jiraAccountId") String jiraAccountId,
    @JsonProperty("githubUsername") String githubUsername
) {
    public static MemberIntegrationsResponse fromEntity(User user) {
        return new MemberIntegrationsResponse(user.getId(), user.getJiraAccountId(), user.getGithubUsername());
    }
}

