package com.samt.projectconfig.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samt.projectconfig.dto.VerificationStatus;
import com.samt.projectconfig.dto.response.VerificationResponse.JiraResult;
import com.samt.projectconfig.exception.VerificationException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Production-hardened service for verifying Jira API credentials.
 * 
 * Resilience Patterns:
 * - Circuit Breaker: Opens after 50% failure rate in 50 requests
 * - Bulkhead (Semaphore): Max 100 concurrent, prevents resource exhaustion
 * - Timeout: 6 seconds enforced by RestTemplate
 * - Fallback: Returns FAILED status with structured error
 * 
 * Test Endpoint: GET {jiraHostUrl}/rest/api/3/myself
 * Success: 200 OK with user info
 * Failure: 401/403/404
 * 
 * @author Production Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JiraVerificationService {
    
    @Qualifier("jiraRestTemplate")
    private final RestTemplate restTemplate;
    
    private final ObjectMapper objectMapper;
    
    @Value("${verification.jira.timeout-seconds:10}")
    private int timeoutSeconds;
    
    /**
     * Verify Jira API credentials with production-grade resilience.
     * 
     * Resilience Configuration:
     * 1. @Async: Executes on dedicated thread pool (verificationExecutor)
     * 2. @Bulkhead(SEMAPHORE): Limits concurrent calls to 100 (lightweight)
     * 3. @CircuitBreaker: Fail-fast when failure rate > 50%
     * 
     * Timeout Strategy:
     * - RestTemplate readTimeout: 6s (enforced at HTTP client level)
     * - No @TimeLimiter (incompatible with @Async)
     * 
     * Exception Handling:
     * - 4xx client errors: Caught and returned as business failure (no circuit trip)
     * - 5xx/timeout/network errors: Propagated to trigger circuit breaker
     * 
     * @param jiraHostUrl Jira host URL (e.g., https://domain.atlassian.net)
     * @param jiraApiToken Decrypted Jira API token
     * @return CompletableFuture of JiraResult with verification status
     */
    @Async("verificationExecutor")
    @Retry(name = "verificationRetry")
    @Bulkhead(name = "jiraVerification", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "jiraVerification", fallbackMethod = "jiraFallback")
    public CompletableFuture<JiraResult> verifyAsync(String jiraHostUrl, String jiraApiToken) {
        Instant testedAt = Instant.now();
        
        try {
            // Build test endpoint URL
            String testUrl = jiraHostUrl.replaceAll("/$", "") + "/rest/api/3/myself";
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jiraApiToken);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.info("Testing Jira API: {} (timeout: {}s)", testUrl, timeoutSeconds);
            
            // Call Jira API (timeout enforced by RestTemplate configuration)
            ResponseEntity<String> response = restTemplate.exchange(
                testUrl,
                HttpMethod.GET,
                request,
                String.class
            );
            
            // Parse response to get user email
            String userEmail;
            try {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                userEmail = jsonResponse.path("emailAddress").asText("unknown");
            } catch (Exception jsonEx) {
                log.warn("Failed to parse Jira response: {}", jsonEx.getMessage());
                userEmail = "unknown";
            }
            
            log.info("Jira verification successful for {} (response time: <{}s)", 
                userEmail, timeoutSeconds);
            
            return CompletableFuture.completedFuture(JiraResult.builder()
                .status(VerificationStatus.SUCCESS.getValue())
                .message("Connected successfully to Jira")
                .testedAt(testedAt)
                .userEmail(userEmail)
                .build());
            
        } catch (HttpClientErrorException e) {
            // Client errors (4xx) - user configuration issue, not service failure
            // Circuit breaker should NOT trip on these
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("Jira API authentication failed: {} - {}", e.getStatusCode(), e.getMessage());
                
                String errorMessage = getErrorMessage(e);
                VerificationStatus status = VerificationStatus.fromHttpStatus(e.getStatusCode().value());
                
                return CompletableFuture.completedFuture(JiraResult.builder()
                    .status(status.getValue())
                    .message("Authentication failed")
                    .error(e.getStatusCode() + " " + errorMessage)
                    .testedAt(testedAt)
                    .build());
            }
            
            // Server errors (5xx) - service failure, should trigger circuit breaker
            log.error("Jira API server error: {} - {}", e.getStatusCode(), e.getMessage());
            throw e;
            
        } catch (ResourceAccessException e) {
            // Timeout or connection error - service failure, should trigger circuit breaker
            log.error("Jira API timeout or connection error: {}", e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // Unexpected errors - should trigger circuit breaker
            log.error("Unexpected error during Jira verification: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Fallback method when circuit breaker is OPEN or bulkhead is full.
     * 
     * Scenarios:
     * - Circuit OPEN: Too many failures, fail-fast to prevent cascading
     * - Bulkhead FULL: Thread pool exhausted, reject new requests
     * - Timeout: Request exceeded time limit
     * 
     * @param jiraHostUrl Jira host URL
     * @param jiraApiToken API token (not logged for security)
     * @param ex Exception that triggered fallback
     * @return CompletableFuture of JiraResult with appropriate failure status
     */
    private CompletableFuture<JiraResult> jiraFallback(String jiraHostUrl, String jiraApiToken, Exception ex) {
        log.error("Jira verification fallback triggered for {}: {} - {}", 
            jiraHostUrl, ex.getClass().getSimpleName(), ex.getMessage());
        
        // Determine failure status based on exception type
        VerificationStatus status;
        String failureReason;
        
        if (ex.getMessage() != null && ex.getMessage().contains("CircuitBreaker")) {
            status = VerificationStatus.FAILED_CIRCUIT_OPEN;
            failureReason = "Circuit breaker OPEN - Jira API experiencing high failure rate";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Bulkhead")) {
            status = VerificationStatus.FAILED_BULKHEAD_FULL;
            failureReason = "Too many concurrent verifications - System at capacity";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Timeout")) {
            status = VerificationStatus.FAILED_TIMEOUT;
            failureReason = "Request exceeded time limit";
        } else {
            status = VerificationStatus.FAILED_EXTERNAL_DEPENDENCY;
            failureReason = "Jira API unavailable - " + ex.getMessage();
        }
        
        return CompletableFuture.completedFuture(JiraResult.builder()
            .status(status.getValue())
            .message("Verification failed - Service temporarily unavailable")
            .error(failureReason)
            .testedAt(Instant.now())
            .build());
    }
    
    /**
     * Extract error message from Jira API error response.
     */
    private String getErrorMessage(HttpClientErrorException e) {
        try {
            JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
            return errorJson.path("errorMessages").path(0).asText(e.getMessage());
        } catch (Exception ex) {
            return e.getMessage();
        }
    }
}
