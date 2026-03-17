package com.example.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamCommitSummaryResponse {
    private Long groupId;
    private LocalDate from;
    private LocalDate to;
    private long totalCommits;
    private long totalPullRequests;
    private long activeContributors;
    private List<MemberCommitSummary> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberCommitSummary {
        private String authorEmail;
        private String authorName;
        private String authorLogin;
        private long commitCount;
        private int additions;
        private int deletions;
        private int totalChanges;
    }
}
