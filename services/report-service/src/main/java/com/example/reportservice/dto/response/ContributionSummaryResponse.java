package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student contribution summary")
public class ContributionSummaryResponse {

    private Long studentId;
    private Long groupId;
    private long taskCount;
    private long completedTaskCount;
    private long githubCommitCount;
    private long githubPrCount;
    private long contributionScore;
    private List<String> recentHighlights;
}