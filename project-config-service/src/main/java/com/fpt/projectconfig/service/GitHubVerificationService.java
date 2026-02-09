package com.fpt.projectconfig.service;

import com.fpt.projectconfig.exception.VerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * UC34: Verify GitHub connection
 * Test bằng cách gọi /user endpoint
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubVerificationService {

    private static final String GITHUB_API = "https://api.github.com";
    private final RestTemplate restTemplate;

    public boolean verify(String githubToken) {
        String endpoint = GITHUB_API + "/user";

        try {
            log.debug("Verifying GitHub token");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + githubToken);
            headers.set("Accept", "application/vnd.github+json");
            headers.set("User-Agent", "ProjectConfigService/1.0");

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("GitHub verification success");
                return true;
            }

            throw new VerificationException("github", "Unexpected response: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("GitHub verification failed: {}", e.getMessage());
            throw new VerificationException("github", e.getMessage(), e);
        }
    }
}
