package com.example.analysisservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRequest {

    @NotBlank
    private String rawRequirements;

    /**
     * When true, the service will use stricter prompts and deterministic settings
     * to maximize schema adherence and validation pass rate.
     */
    private boolean strict = false;
}
