package com.example.analysisservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRequest {

    @NotBlank
    private String rawRequirements;
}
