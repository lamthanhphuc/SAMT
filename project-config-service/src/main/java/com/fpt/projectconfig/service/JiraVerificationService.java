package com.fpt.projectconfig.service;

import com.fpt.projectconfig.exception.VerificationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * UC34: Verify Jira connection
 * Test bằng cách gọi /rest/api/3/myself endpoint
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JiraVerificationService {

    private final RestTemplate restTemplate;

    public boolean verify(String jiraHostUrl, String jiraApiToken) {
        String endpoint = jiraHostUrl.endsWith("/")
                ? jiraHostUrl + "rest/api/3/myself"
                : jiraHostUrl + "/rest/api/3/myself";

        try {
            log.debug("Verifying Jira: {}", jiraHostUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jiraApiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Jira verification success: {}", jiraHostUrl);
                return true;
            }

            throw new VerificationException("jira", "Unexpected response: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Jira verification failed: {}", e.getMessage());
            throw new VerificationException("jira", e.getMessage(), e);
        }
    }
}
