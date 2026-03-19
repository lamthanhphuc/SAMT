package com.example.reportservice.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiStructuredResponse {
    private String srsContent;
    private List<RequirementItem> requirements;
    private List<String> openQuestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequirementItem {
        private String id;
        private String type;
        private String title;
        private String description;
        private List<String> sourceRefs;
    }
}
