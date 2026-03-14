package com.example.syncservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for GitHub commit from GitHub REST API response.
 * Maps to GitHub API v3 structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubCommitDto {

    private String sha;
    
    @JsonProperty("commit")
    private CommitDetails commit;
    
    @JsonProperty("author")
    private Author author;
    
    @JsonProperty("stats")
    private Stats stats;
    
    @JsonProperty("files")
    private List<FileChange> files;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitDetails {
        private String message;
        
        @JsonProperty("author")
        private CommitAuthor author;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitAuthor {
        private String name;
        private String email;
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String login;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stats {
        private Integer additions;
        private Integer deletions;
        private Integer total;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileChange {
        private String filename;
        private Integer additions;
        private Integer deletions;
        private Integer changes;
        private String status;
    }
}
