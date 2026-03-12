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
@Schema(description = "Normalized activity item")
public class RecentActivityResponse {

    private Long activityId;
    private String source;
    private String type;
    private String title;
    private String author;
    private LocalDateTime occurredAt;
    private String externalId;
    private String url;
}