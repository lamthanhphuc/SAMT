package com.example.reportservice.service;

import com.example.reportservice.config.AiServiceProperties;
import com.example.reportservice.config.CorrelationIdFilter;
import com.example.reportservice.dto.ai.AiRequest;
import com.example.reportservice.dto.ai.AiResponse;
import com.example.reportservice.web.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Retry(name = "analysisApi")
    @CircuitBreaker(name = "analysisApi")
    public String generateSrs(String rawContent) {

        String url = properties.getUrl() + "/internal/ai/generate-srs";

        AiRequest request = AiRequest.builder()
                .rawRequirements(rawContent)
                .build();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            relayAuthorization(headers);

            ResponseEntity<AiResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                            AiResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
            throw new UpstreamServiceException(
                        "AI Service error: " + response.getStatusCode());
            }

            AiResponse body = response.getBody();

            if (body == null || body.getSrsContent() == null) {
                throw new UpstreamServiceException("Invalid AI response");
            }

            return body.getSrsContent();

        } catch (RestClientException e) {

            log.error("Failed to call AI Service", e);
            throw new UpstreamServiceException("AI Service unavailable");
        }
    }

    private void relayAuthorization(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
            String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (requestId != null && !requestId.isBlank()) {
                headers.set(REQUEST_ID_HEADER, requestId);
            }
            return;
        }

        throw new UpstreamServiceException("Missing caller JWT for internal AI call");
    }
}