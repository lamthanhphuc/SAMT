package com.example.identityservice.service;

import com.example.identityservice.exception.BadGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class JiraIntegrationServiceTest {

    @Test
    void findAccountIdByEmail_whenHostMissingScheme_defaultsToHttps() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        JiraIntegrationService svc = new JiraIntegrationService(rt);
        ReflectionTestUtils.setField(svc, "jiraHost", "my-company.atlassian.net"); // missing scheme
        ReflectionTestUtils.setField(svc, "jiraEmail", "jira-bot@example.com");
        ReflectionTestUtils.setField(svc, "jiraApiToken", "dummy_token");

        String email = "phucltse184678@fpt.edu.vn";
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(("jira-bot@example.com:dummy_token").getBytes(StandardCharsets.UTF_8));

        server.expect(once(), requestTo("https://my-company.atlassian.net/rest/api/3/user/search?query=" + email))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
            .andRespond(withStatus(OK).contentType(MediaType.APPLICATION_JSON).body("[{\"accountId\":\"acc-1\"}]"));

        String accountId = svc.findAccountIdByEmail(email);
        assertEquals("acc-1", accountId);

        server.verify();
    }

    @Test
    void findAccountIdByEmail_callsUserSearchWithBasicAuth_andParsesAccountId() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        JiraIntegrationService svc = new JiraIntegrationService(rt);
        ReflectionTestUtils.setField(svc, "jiraHost", "https://my-company.atlassian.net");
        ReflectionTestUtils.setField(svc, "jiraEmail", "jira-bot@example.com");
        ReflectionTestUtils.setField(svc, "jiraApiToken", "dummy_token");

        String email = "phucltse184678@fpt.edu.vn";
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(("jira-bot@example.com:dummy_token").getBytes(StandardCharsets.UTF_8));

        String responseBody = "[{\"accountId\":\"abc-123\",\"displayName\":\"Phuc LT\"}]";

        server.expect(once(), requestTo("https://my-company.atlassian.net/rest/api/3/user/search?query=" + email))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
            .andRespond(withStatus(OK).contentType(MediaType.APPLICATION_JSON).body(responseBody));

        String accountId = svc.findAccountIdByEmail(email);
        System.out.println("findAccountIdByEmail_callsUserSearch... -> accountId=" + accountId);
        assertEquals("abc-123", accountId);

        server.verify();
    }

    @Test
    void findAccountIdByEmail_whenJiraReturns401_throwsBadGatewayWithDetails() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        JiraIntegrationService svc = new JiraIntegrationService(rt);
        ReflectionTestUtils.setField(svc, "jiraHost", "https://my-company.atlassian.net");
        ReflectionTestUtils.setField(svc, "jiraEmail", "jira-bot@example.com");
        ReflectionTestUtils.setField(svc, "jiraApiToken", "wrong_token");

        String email = "phucltse184678@fpt.edu.vn";
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(("jira-bot@example.com:wrong_token").getBytes(StandardCharsets.UTF_8));

        String body = "{\"errorMessages\":[\"Client must be authenticated to access this resource.\"],\"errors\":{}}";

        server.expect(once(), requestTo("https://my-company.atlassian.net/rest/api/3/user/search?query=" + email))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
            .andRespond(withStatus(UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(body));

        BadGatewayException ex = assertThrows(BadGatewayException.class, () -> svc.findAccountIdByEmail(email));
        System.out.println("findAccountIdByEmail_whenJiraReturns401 -> " + ex.getMessage());

        server.verify();
    }
}

