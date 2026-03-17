package com.example.analysisservice.client;

import com.example.analysisservice.config.LocalAiProperties;
import com.example.analysisservice.dto.request.LocalAiGenerateRequest;
import com.example.analysisservice.dto.response.LocalAiGenerateResponse;
import com.example.analysisservice.web.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class LocalAiClient {

    private final RestTemplate restTemplate;
    private final LocalAiProperties properties;

    @Retry(name = "localAi", fallbackMethod = "callFallback")
    @CircuitBreaker(name = "localAi", fallbackMethod = "callFallback")
    public String call(LocalAiGenerateRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<LocalAiGenerateRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<LocalAiGenerateResponse> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/api/generate",
                    HttpMethod.POST,
                    entity,
                    LocalAiGenerateResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new UpstreamServiceException("Local AI returned non-success status: " + response.getStatusCode());
            }

            LocalAiGenerateResponse body = response.getBody();
            if (body == null || body.getResponse() == null || body.getResponse().isBlank()) {
                throw new UpstreamServiceException("Local AI response is empty or invalid");
            }

            return body.getResponse();
        } catch (ResourceAccessException ex) {
            throw ex;
        } catch (UpstreamServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UpstreamServiceException("Local AI request failed", ex);
        }
    }

    @SuppressWarnings("unused")
    private String callFallback(LocalAiGenerateRequest request, Throwable throwable) {
        throw new UpstreamServiceException("Local AI dependency unavailable", throwable);
    }
}
