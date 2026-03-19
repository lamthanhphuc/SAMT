package com.example.analysisservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiStructuredResponse {

    private String srsContent;
    private List<RequirementItem> requirements;
    private List<String> openQuestions;

    @Data
    @AllArgsConstructor
    public static class RequirementItem {
        private String id;
        private String type;
        private String title;
        private String description;
        private List<String> sourceRefs;
    }
}
