package com.samt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for verification endpoint.
 * Contains results of testing Jira and GitHub APIs.
 */
@Builder
public record VerificationResponse(
    UUID configId,
    String state,
    VerificationResults verificationResults,
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant lastVerifiedAt,
    
    String invalidReason
) {
    
    @Builder
    public record VerificationResults(
        JiraResult jira,
        GitHubResult github
    ) {}
    
    @Builder
    public record JiraResult(
        String status,  // SUCCESS, FAILED, PENDING
        String message,
        String error,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant testedAt,
        
        String userEmail
    ) {}
    
    @Builder
    public record GitHubResult(
        String status,  // SUCCESS, FAILED, PENDING
        String message,
        String error,
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant testedAt,
        
        String repoName,
        Boolean hasWriteAccess
    ) {}
}
