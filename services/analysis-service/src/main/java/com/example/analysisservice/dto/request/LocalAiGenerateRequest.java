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
    /**
     * Ollama response format. When set to "json", Ollama will constrain output to valid JSON.
     */
    private String format;
    private Options options;

    @Data
    @Builder
    public static class Options {
        @JsonProperty("num_ctx")
        private int numCtx;

        @JsonProperty("temperature")
        private Double temperature;

        /**
         * Max tokens to generate. Lower values reduce latency/cost and help avoid timeouts.
         * Ollama option name: num_predict
         */
        @JsonProperty("num_predict")
        private Integer numPredict;
    }
}
