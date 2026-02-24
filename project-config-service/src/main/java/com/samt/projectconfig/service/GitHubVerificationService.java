package com.samt.projectconfig.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samt.projectconfig.dto.VerificationStatus;
import com.samt.projectconfig.dto.response.VerificationResponse.GitHubResult;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for verifying GitHub API credentials with circuit breaker protection.
 * 
 * Test endpoint: GET https://api.github.com/repos/{owner}/{repo}
 * Success: 200 OK with repo info + permissions.push = true
 * Failure: 401/403/404
 * 
 * BR-VERIFY-01: Enforced timeout 6 seconds (configured in RestTemplateConfig)
 * 
 * Resilience Strategy:
 * - Circuit Breaker: Fail-fast when GitHub API experiences high failure rate
 * - Semaphore Bulkhead: Isolate GitHub API calls (max 100 concurrent)
 * - Timeout: 6s enforced at RestTemplate level
 * 
 * Fallback Behavior: Returns FAILED status when circuit OPEN or bulkhead FULL
 */
@Service
@Slf4j
public class GitHubVerificationService {
    
    private final RestTemplate githubRestTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${verification.github.timeout-seconds:10}")
    private int timeoutSeconds;
    
    public GitHubVerificationService(
        @Qualifier("githubRestTemplate") RestTemplate githubRestTemplate,
        ObjectMapper objectMapper
    ) {
        this.githubRestTemplate = githubRestTemplate;
        this.objectMapper = objectMapper;
    }
    
    private static final Pattern GITHUB_REPO_PATTERN = 
        Pattern.compile("https://github\\.com/([\\w-]+)/([\\w-]+)");
    
    /**
     * Verify GitHub API credentials by calling GitHub REST API.
     * Protected by circuit breaker and semaphore bulkhead.
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
     * @param githubRepoUrl GitHub repository URL (e.g., https://github.com/owner/repo)
     * @param githubToken Decrypted GitHub Personal Access Token
     * @return CompletableFuture of GitHubResult with verification status
     */
    @Async("verificationExecutor")
    @Retry(name = "verificationRetry")
    @Bulkhead(name = "githubVerification", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "githubVerification", fallbackMethod = "githubFallback")
    public CompletableFuture<GitHubResult> verifyAsync(String githubRepoUrl, String githubToken) {
        Instant testedAt = Instant.now();
        
        try {
            // Extract owner and repo from URL
            Matcher matcher = GITHUB_REPO_PATTERN.matcher(githubRepoUrl);
            if (!matcher.matches()) {
                return CompletableFuture.completedFuture(GitHubResult.builder()
                    .status(VerificationStatus.FAILED_BAD_REQUEST.getValue())
                    .message("Invalid GitHub repository URL format")
                    .error("URL does not match pattern: https://github.com/owner/repo")
                    .testedAt(testedAt)
                    .build());
            }
            
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            
            // Build test endpoint URL
            String testUrl = String.format("https://api.github.com/repos/%s/%s", owner, repo);
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + githubToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.set("X-GitHub-Api-Version", "2022-11-28");
            
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            log.info("Testing GitHub API: {} (timeout: {}s)", testUrl, timeoutSeconds);
            
            // Call GitHub API with enforced timeout
            ResponseEntity<String> response = githubRestTemplate.exchange(
                testUrl,
                HttpMethod.GET,
                request,
                String.class
            );
            
            // Parse response to check permissions
            String repoFullName;
            boolean hasWriteAccess;
            try {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                repoFullName = jsonResponse.path("full_name").asText(owner + "/" + repo);
                hasWriteAccess = jsonResponse.path("permissions").path("push").asBoolean(false);
            } catch (Exception jsonEx) {
                log.warn("Failed to parse GitHub response: {}", jsonEx.getMessage());
                repoFullName = owner + "/" + repo;
                hasWriteAccess = false;
            }
            
            log.info("GitHub verification SUCCESS for {} (write access: {})", 
                repoFullName, hasWriteAccess);
            
            return CompletableFuture.completedFuture(GitHubResult.builder()
                .status(VerificationStatus.SUCCESS.getValue())
                .message("Connected successfully to GitHub")
                .testedAt(testedAt)
                .repoName(repoFullName)
                .hasWriteAccess(hasWriteAccess)
                .build());
            
        } catch (HttpClientErrorException e) {
            // Client errors (4xx) - user configuration issue, not service failure
            // Circuit breaker should NOT trip on these
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("GitHub verification FAILED: repo={} status={} error={}", 
                    githubRepoUrl, e.getStatusCode(), e.getMessage());
                
                String errorMessage = getErrorMessage(e);
                VerificationStatus status = VerificationStatus.fromHttpStatus(e.getStatusCode().value());
                
                return CompletableFuture.completedFuture(GitHubResult.builder()
                    .status(status.getValue())
                    .message("Authentication failed or repository not found")
                    .error(e.getStatusCode() + " " + errorMessage)
                    .testedAt(testedAt)
                    .build());
            }
            
            // Server errors (5xx) - service failure, should trigger circuit breaker
            log.error("GitHub API server error: {} - {}", e.getStatusCode(), e.getMessage());
            throw e;
            
        } catch (ResourceAccessException e) {
            // Timeout or connection error - service failure, should trigger circuit breaker
            log.error("GitHub API timeout or connection error: {}", e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // Unexpected errors - should trigger circuit breaker
            log.error("Unexpected error during GitHub verification: {}", e.getMessage(), e);
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
     * @param githubRepoUrl GitHub repository URL
     * @param githubToken API token (not logged for security)
     * @param ex Exception that triggered fallback
     * @return CompletableFuture of GitHubResult with appropriate failure status
     */
    private CompletableFuture<GitHubResult> githubFallback(String githubRepoUrl, String githubToken, Exception ex) {
        log.error("GitHub verification fallback triggered for {}: {} - {}", 
            githubRepoUrl, ex.getClass().getSimpleName(), ex.getMessage());
        
        // Determine failure status based on exception type
        VerificationStatus status;
        String failureReason;
        
        if (ex.getMessage() != null && ex.getMessage().contains("CircuitBreaker")) {
            status = VerificationStatus.FAILED_CIRCUIT_OPEN;
            failureReason = "Circuit breaker OPEN - GitHub API experiencing high failure rate";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Bulkhead")) {
            status = VerificationStatus.FAILED_BULKHEAD_FULL;
            failureReason = "Too many concurrent verifications - System at capacity";
        } else if (ex.getMessage() != null && ex.getMessage().contains("Timeout")) {
            status = VerificationStatus.FAILED_TIMEOUT;
            failureReason = "Request exceeded time limit";
        } else {
            status = VerificationStatus.FAILED_EXTERNAL_DEPENDENCY;
            failureReason = "GitHub API unavailable - " + ex.getMessage();
        }
        
        return CompletableFuture.completedFuture(GitHubResult.builder()
            .status(status.getValue())
            .message("Verification failed - Service temporarily unavailable")
            .error(failureReason)
            .testedAt(Instant.now())
            .build());
    }
    
    /**
     * Extract error message from GitHub API error response.
     */
    private String getErrorMessage(HttpClientErrorException e) {
        try {
            JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
            return errorJson.path("message").asText(e.getMessage());
        } catch (Exception ex) {
            return e.getMessage();
        }
    }
}
