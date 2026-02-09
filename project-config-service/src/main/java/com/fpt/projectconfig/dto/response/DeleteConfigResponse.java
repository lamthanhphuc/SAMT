package com.fpt.projectconfig.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for delete operation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteConfigResponse {

    private String message;
    private UUID configId;
    private Instant deletedAt;
    private int retentionDays;
}
