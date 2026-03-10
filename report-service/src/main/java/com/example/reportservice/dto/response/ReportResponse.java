package com.example.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private UUID reportId;

    private String filePath;

    private String message;
}
