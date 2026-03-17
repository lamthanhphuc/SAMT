package com.example.reportservice.service;

import lombok.Builder;

@Builder
public record EvidenceBlock(
        String sourceType,
        String sourceId,
        String summary,
        String description,
        String status,
        String timestamp
) {
}
