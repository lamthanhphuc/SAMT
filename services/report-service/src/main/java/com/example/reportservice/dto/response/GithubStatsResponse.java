package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student GitHub statistics")
public class GithubStatsResponse {

    private long commitCount;
    private long prCount;
    private long mergedPrCount;
    private long reviewCount;
    private long activeDays;
    private LocalDateTime lastCommitAt;
}