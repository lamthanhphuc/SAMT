package com.example.syncservice.client.external;

import com.example.syncservice.dto.JiraIssueDto;
import com.example.syncservice.service.FallbackSignal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JiraClientTest {

    @Test
    void fetchIssues_usesSearchJqlEndpoint_basicAuth_andParsesAdfDescription() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            String payload = """
                    {
                      \"issues\": [
                        {
                          \"id\": \"10001\",
                          \"key\": \"SAMT-1\",
                          \"fields\": {
                            \"summary\": \"Fix sync bug\",
                            \"description\": {
                              \"type\": \"doc\",
                              \"content\": [
                                {
                                  \"type\": \"paragraph\",
                                  \"content\": [
                                    { \"type\": \"text\", \"text\": \"Line one\" },
                                    { \"type\": \"text\", \"text\": \"Line two\" }
                                  ]
                                }
                              ]
                            },
                            \"created\": \"2026-03-09T10:00:00Z\",
                            \"updated\": \"2026-03-09T10:05:00Z\",
                            \"issuetype\": { \"name\": \"Bug\" },
                            \"status\": { \"name\": \"Open\" }
                          }
                        }
                      ]
                    }
                    """;
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .build());
        };

        JiraClient jiraClient = new JiraClient(
                WebClient.builder().exchangeFunction(exchangeFunction).build(),
                new FallbackSignal());

        List<JiraIssueDto> issues = jiraClient.fetchIssues("https://example.atlassian.net", "user@example.com", "token-123", 50);

        assertThat(capturedRequest.get().method().name()).isEqualTo("POST");
        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/rest/api/3/search/jql");
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(capturedRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("user@example.com:token-123".getBytes()));
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getFields().getDescription()).contains("Line one").contains("Line two");
    }
}