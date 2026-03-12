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
@Schema(description = "Lecturer dashboard overview metrics")
public class LecturerOverviewResponse {

    private Long lecturerId;
    private Long semesterId;
    private long groupCount;
    private long studentCount;
    private long taskCount;
    private long completedTaskCount;
    private long githubCommitCount;
    private long githubPrCount;
    private LocalDateTime lastSyncAt;
}