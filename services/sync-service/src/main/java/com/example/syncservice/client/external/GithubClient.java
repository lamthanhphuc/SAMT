package com.example.syncservice.client.external;

import com.example.syncservice.dto.GithubCommitDto;
import com.example.syncservice.service.FallbackSignal;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Client for GitHub REST API.
 * 
 * CRITICAL DESIGN:
 * - WebClient for non-blocking calls
 * - Must be called OUTSIDE @Transactional
 * - Rate limit: 5000 req/hour (authenticated)
 * - Exponential backoff with jitter
 * - Circuit breaker protects against API downtime
 * - Handles 403 (rate limit) explicitly
 */
@Component
@Slf4j
public class GithubClient {

    private final WebClient githubWebClient;
    private final FallbackSignal fallbackSignal;

    public GithubClient(@Qualifier("githubWebClient") WebClient githubWebClient,
                        FallbackSignal fallbackSignal) {
        this.githubWebClient = githubWebClient;
        this.fallbackSignal = fallbackSignal;
    }

    /**
     * Fetch commits from GitHub repository.
     * 
     * @param repoUrl Repository URL (e.g., https://github.com/owner/repo)
     * @param accessToken GitHub personal access token
     * @param perPage Number of commits per request (max 100)
     * @return List of GitHub commits
     */
    @Retry(name = "githubRetry", fallbackMethod = "fetchCommitsFallback")
    @CircuitBreaker(name = "githubCircuitBreaker", fallbackMethod = "fetchCommitsFallback")
    @RateLimiter(name = "githubRateLimiter")
    public List<GithubCommitDto> fetchCommits(String repoUrl, String accessToken, int perPage) {
        log.debug("Fetching GitHub commits for repo={}", repoUrl);

        String[] parts = extractOwnerAndRepo(repoUrl);
        String owner = parts[0];
        String repo = parts[1];

        try {
            List<GithubCommitDto> commits = githubWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repos/{owner}/{repo}/commits")
                            .queryParam("per_page", perPage)
                            .build(owner, repo))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        if (clientResponse.statusCode() == HttpStatus.FORBIDDEN) {
                            log.warn("GitHub rate limit exceeded (403)");
                            return Mono.error(new RateLimitExceededException("GitHub rate limit exceeded"));
                        } else if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                            log.error("GitHub authentication failed (401)");
                            return Mono.error(new AuthenticationException("GitHub token invalid"));
                        } else if (clientResponse.statusCode() == HttpStatus.NOT_FOUND) {
                            log.error("GitHub repository not found (404): {}/{}", owner, repo);
                            return Mono.error(new RepositoryNotFoundException("Repository not found: " + owner + "/" + repo));
                        }
                        return clientResponse.createException();
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        log.error("GitHub server error: {}", clientResponse.statusCode());
                        return clientResponse.createException();
                    })
                    .bodyToMono(new ParameterizedTypeReference<List<GithubCommitDto>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (commits == null || commits.isEmpty()) {
                log.info("No commits found for repo={}/{}", owner, repo);
                return List.of();
            }

            log.info("Fetched {} commits from GitHub repo={}/{}", commits.size(), owner, repo);
            return commits;

        } catch (RateLimitExceededException e) {
            log.error("GitHub rate limit exceeded for repo={}/{}", owner, repo);
            throw e;
        } catch (AuthenticationException e) {
            log.error("GitHub authentication failed for repo={}/{}", owner, repo);
            throw e;
        } catch (RepositoryNotFoundException e) {
            log.error("GitHub repository not found: {}/{}", owner, repo);
            throw e;
        } catch (Exception e) {
            log.error("Error fetching GitHub commits for repo={}/{}: {}", owner, repo, e.getMessage(), e);
            throw new GithubClientException("Failed to fetch GitHub commits: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback method for fetchCommits.
     * Called when retry exhausted or circuit breaker open.
     * 
     * CRITICAL: Sets degraded execution flag for orchestrator to detect.
     */
    private List<GithubCommitDto> fetchCommitsFallback(String repoUrl, String accessToken, int perPage, Throwable throwable) {
        String errorMsg = String.format("GitHub API unavailable for repo=%s: %s", 
                repoUrl, throwable.getMessage());
        
        log.warn("⚠️ FALLBACK TRIGGERED: {}", errorMsg, throwable);
        
        // Signal degraded execution to orchestrator
        fallbackSignal.setDegraded(true, errorMsg);
        
        // Return safe empty result (prevents exception propagation)
        return List.of();
    }

    /**
     * Extract owner and repo from GitHub URL.
     * 
     * @param repoUrl GitHub repo URL (e.g., https://github.com/owner/repo)
     * @return [owner, repo]
     */
    private String[] extractOwnerAndRepo(String repoUrl) {
        String path = repoUrl.replace("https://github.com/", "").replace("http://github.com/", "");
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        String[] parts = path.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid GitHub repo URL: " + repoUrl);
        }
        return parts;
    }

    // Custom exceptions
    public static class GithubClientException extends RuntimeException {
        public GithubClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }

    public static class RepositoryNotFoundException extends RuntimeException {
        public RepositoryNotFoundException(String message) {
            super(message);
        }
    }
}
