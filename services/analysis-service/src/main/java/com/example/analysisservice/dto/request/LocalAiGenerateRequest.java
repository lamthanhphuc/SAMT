package com.example.analysisservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalAiGenerateRequest {

    private String model;
    private String prompt;
    private boolean stream;
    private Options options;

    @Data
    @Builder
    public static class Options {
        @JsonProperty("num_ctx")
        private int numCtx;
    }
}
