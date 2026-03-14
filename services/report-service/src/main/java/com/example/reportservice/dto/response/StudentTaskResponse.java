package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student task row")
public class StudentTaskResponse {

    private String taskId;
    private String source;
    private String key;
    private String title;
    private String status;
    private String priority;
    private Long groupId;
    private String groupName;
    private String assignee;
    private OffsetDateTime updatedAt;
    private String url;
}