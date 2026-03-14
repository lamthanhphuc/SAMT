package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Group task progress metrics")
public class GroupProgressResponse {

    private Long groupId;
    private String groupName;
    private double completionRate;
    private long todoCount;
    private long inProgressCount;
    private long doneCount;
    private Map<String, Long> taskByType;
    private Map<String, Long> taskByStatus;
}