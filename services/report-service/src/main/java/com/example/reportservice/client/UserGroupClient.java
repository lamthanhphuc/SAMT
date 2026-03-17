package com.example.reportservice.client;

import com.example.reportservice.config.InternalServiceProperties;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import com.example.reportservice.web.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserGroupClient {

    private final RestTemplate restTemplate;
    private final InternalServiceProperties properties;
    private final AuthenticatedRequestSupport requestSupport;

    public List<GroupSummary> listGroups(Long lecturerId, Long semesterId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getUserGroupBaseUrl())
            .path("/api/groups")
            .queryParam("page", 0)
            .queryParam("size", 100);
        if (lecturerId != null) {
            builder.queryParam("lecturerId", lecturerId);
        }
        if (semesterId != null) {
            builder.queryParam("semesterId", semesterId);
        }
        JsonNode body = get(builder.toUriString());
        List<GroupSummary> groups = new ArrayList<>();
        JsonNode content = body.path("data").path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                groups.add(new GroupSummary(
                    item.path("id").asLong(),
                    item.path("groupName").asText(),
                    item.path("semesterId").asLong(),
                    item.path("semesterCode").asText(null),
                    item.path("memberCount").asLong()
                ));
            }
        }
        return groups;
    }

    public GroupDetail getGroup(Long groupId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getUserGroupBaseUrl())
            .path("/api/groups/{groupId}")
            .buildAndExpand(groupId)
            .toUriString();
        JsonNode data = get(url).path("data");
        return new GroupDetail(
            data.path("id").asLong(),
            data.path("groupName").asText(),
            data.path("semesterId").asLong(),
            data.path("memberCount").asLong(),
            data.path("lecturer").path("id").asLong()
        );
    }

    public UserProfile getUserProfile(Long userId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getUserGroupBaseUrl())
            .path("/api/users/{userId}")
            .buildAndExpand(userId)
            .toUriString();
        JsonNode data = get(url).path("data");
        return new UserProfile(
            data.path("id").asLong(),
            data.path("email").asText(),
            data.path("fullName").asText()
        );
    }

    public List<UserGroupMembership> getUserGroups(Long userId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getUserGroupBaseUrl())
            .path("/api/users/{userId}/groups")
            .buildAndExpand(userId)
            .toUriString();
        JsonNode body = get(url);
        List<UserGroupMembership> groups = new ArrayList<>();
        JsonNode items = body.path("data").path("groups");
        if (items.isArray()) {
            for (JsonNode item : items) {
                groups.add(new UserGroupMembership(
                    item.path("groupId").asLong(),
                    item.path("groupName").asText(),
                    item.path("semesterId").asLong(),
                    item.path("semesterCode").asText(null),
                    item.path("role").asText(null),
                    item.path("lecturerName").asText(null)
                ));
            }
        }
        return groups;
    }

    public long countUsers() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(properties.getUserGroupBaseUrl())
            .path("/api/users")
            .queryParam("page", 0)
            .queryParam("size", 1);

        JsonNode body = get(builder.toUriString());
        return body.path("data").path("totalElements").asLong(0);
    }

    private JsonNode get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        requestSupport.applyCallerHeaders(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new EntityNotFoundException("Requested resource not found");
            }
            throw new UpstreamServiceException("user-group-service unavailable", ex);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("user-group-service unavailable", ex);
        }
    }

    public record GroupSummary(
        Long groupId,
        String groupName,
        Long semesterId,
        String semesterCode,
        Long memberCount
    ) {
    }

    public record GroupDetail(
        Long groupId,
        String groupName,
        Long semesterId,
        Long memberCount,
        Long lecturerId
    ) {
    }

    public record UserProfile(
        Long userId,
        String email,
        String fullName
    ) {
    }

    public record UserGroupMembership(
        Long groupId,
        String groupName,
        Long semesterId,
        String semesterCode,
        String role,
        String lecturerName
    ) {
    }
}