package com.example.analysisservice.service;


import com.example.analysisservice.client.OpenAiClient;
import com.example.analysisservice.config.OpenAiProperties;
import com.example.analysisservice.dto.request.OpenAiRequest;
import com.example.analysisservice.web.BadRequestException;
import com.example.analysisservice.web.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiService implements AiService {

    private final OpenAiClient client;
    private final OpenAiProperties properties;

    @Override
    public String generateSrs(String rawRequirements) {

        if (rawRequirements == null || rawRequirements.isBlank()) {
            throw new BadRequestException("Raw requirements must not be empty");
        }

        String prompt = PromptBuilder.buildSrsPrompt(rawRequirements);

        OpenAiRequest request = OpenAiRequest.builder()
                .model(properties.getModel())
                .temperature(0.3)
                .messages(List.of(
                        OpenAiRequest.Message.builder()
                                .role("system")
                                .content("You are a professional system analyst.")
                                .build(),
                        OpenAiRequest.Message.builder()
                                .role("user")
                                .content(prompt)
                                .build()
                ))
                .build();

        try {
            return client.call(request);
        } catch (Exception ex) {
            throw new UpstreamServiceException("OpenAI call failed", ex);
        }
    }
}

