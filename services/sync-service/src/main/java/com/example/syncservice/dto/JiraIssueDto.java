package com.example.syncservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Jira issue from Jira REST API response.
 * Maps to Jira Cloud API v3 structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDto {

    private String key;
    private String id;
    
    @JsonProperty("fields")
    private Fields fields;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String summary;
        private String description;
        
        @JsonProperty("issuetype")
        private IssueType issueType;
        
        @JsonProperty("status")
        private Status status;
        
        @JsonProperty("assignee")
        private User assignee;
        
        @JsonProperty("reporter")
        private User reporter;
        
        @JsonProperty("priority")
        private Priority priority;
        
        private String created;
        private String updated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueType {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String emailAddress;
        private String displayName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Priority {
        private String name;
    }
}
