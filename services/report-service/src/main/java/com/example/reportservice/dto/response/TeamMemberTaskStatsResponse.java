package com.example.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberTaskStatsResponse {
    private Long groupId;
    private Long memberId;
    private long totalAssigned;
    private long completed;
    private long inProgress;
    private long todo;
    private double completionRate;
}
