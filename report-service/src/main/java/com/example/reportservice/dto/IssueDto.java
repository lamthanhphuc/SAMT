package com.example.reportservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IssueDto {

    private String issueId;
    private String issueKey;
    private String summary;
    private String status;
    private String priority;
}
