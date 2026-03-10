package com.example.analysisservice.client;
import com.example.analysisservice.config.OpenAiProperties;
import com.example.analysisservice.dto.request.OpenAiRequest;
import com.example.analysisservice.dto.response.OpenAiResponse;
import com.example.analysisservice.web.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;

        @Retry(name = "openAi")
        @CircuitBreaker(name = "openAi")
    public String call(OpenAiRequest request) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OpenAiRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<OpenAiResponse> response =
                restTemplate.exchange(
                        properties.getUrl(),
                        HttpMethod.POST,
                        entity,
                        OpenAiResponse.class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new UpstreamServiceException("OpenAI returned non-success status: " + response.getStatusCode());
                }

                OpenAiResponse body = response.getBody();
                if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
                        throw new UpstreamServiceException("OpenAI response is empty");
                }

                OpenAiResponse.Message message = body.getChoices().getFirst().getMessage();
                if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                        throw new UpstreamServiceException("OpenAI response content missing");
                }

                return message.getContent();
    }
}