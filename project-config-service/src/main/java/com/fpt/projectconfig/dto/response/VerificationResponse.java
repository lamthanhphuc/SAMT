package com.fpt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationResponse {
    private UUID configId;
    private String state;
    private VerificationResults verificationResults;
    private String invalidReason;

    @Data
    @Builder
    public static class VerificationResults {
        private ServiceResult jira;
        private ServiceResult github;
    }

    @Data
    @Builder
    public static class ServiceResult {
        private String status; // SUCCESS, FAILED, PENDING
        private String message;
        private String error;
    }
}
