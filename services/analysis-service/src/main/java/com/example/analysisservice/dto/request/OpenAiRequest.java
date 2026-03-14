package com.example.analysisservice.dto.request;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenAiRequest {

    private String model;
    private List<Message> messages;
    private Double temperature;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
