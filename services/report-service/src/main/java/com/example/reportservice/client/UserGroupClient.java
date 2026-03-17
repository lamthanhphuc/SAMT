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
import java.util.Objects;

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
        List<Long> memberUserIds = new ArrayList<>();
        List<GroupMember> membersDetailed = new ArrayList<>();
        JsonNode members = data.path("members");
        if (members.isArray()) {
            for (JsonNode member : members) {
                JsonNode userIdNode = member.path("userId");
                Long userId = null;
                if (userIdNode.canConvertToLong()) {
                    userId = userIdNode.asLong();
                } else {
                    JsonNode nestedIdNode = member.path("user").path("id");
                    if (nestedIdNode.canConvertToLong()) {
                        userId = nestedIdNode.asLong();
                    }
                }

                if (userId != null) {
                    memberUserIds.add(userId);
                }

                String fullName = textOrNull(member.path("fullName"));
                String email = textOrNull(member.path("email"));
                JsonNode userNode = member.path("user");
                if (fullName == null) {
                    fullName = textOrNull(userNode.path("fullName"));
                }
                if (email == null) {
                    email = textOrNull(userNode.path("email"));
                }
                String jiraAccountId = textOrNull(member.path("jiraAccountId"));
                if (jiraAccountId == null) {
                    jiraAccountId = textOrNull(userNode.path("jiraAccountId"));
                }
                String role = textOrNull(member.path("role"));
                if (role == null) {
                    role = textOrNull(member.path("groupRole"));
                }
                membersDetailed.add(new GroupMember(userId, email, fullName, role, jiraAccountId));
            }
        }
        return new GroupDetail(
            data.path("id").asLong(),
            data.path("groupName").asText(),
            data.path("semesterId").asLong(),
            data.path("memberCount").asLong(),
            data.path("lecturer").path("id").asLong(),
            memberUserIds.stream().filter(Objects::nonNull).distinct().toList(),
            membersDetailed.stream().filter(item -> item.userId() != null).toList()
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
            data.path("fullName").asText(),
            textOrNull(data.path("jiraAccountId"))
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

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
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
        Long lecturerId,
        List<Long> memberUserIds,
        List<GroupMember> members
    ) {
    }

    public record GroupMember(
        Long userId,
        String email,
        String fullName,
        String role,
        String jiraAccountId
    ) {
    }

    public record UserProfile(
        Long userId,
        String email,
        String fullName,
        String jiraAccountId
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