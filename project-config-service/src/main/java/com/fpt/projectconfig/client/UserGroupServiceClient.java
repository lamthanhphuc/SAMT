package com.fpt.projectconfig.client;

import com.fpt.projectconfig.client.dto.GroupDto;
import com.fpt.projectconfig.exception.GroupNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * REST client cho User-Group Service
 * TODO: Update endpoints khi User-Group Service ready
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserGroupServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.user-group.base-url:http://localhost:8082}")
    private String baseUrl;

    public GroupDto getGroup(UUID groupId) {
        String url = baseUrl + "/api/groups/" + groupId;

        try {
            log.debug("Getting group: {}", groupId);
            ResponseEntity<GroupDto> response = restTemplate.getForEntity(url, GroupDto.class);

            if (response.getBody() != null) {
                GroupDto group = response.getBody();
                if ("DELETED".equals(group.getStatus())) {
                    throw new GroupNotFoundException(groupId);
                }
                return group;
            }

            throw new GroupNotFoundException(groupId);

        } catch (Exception e) {
            log.error("Failed to get group: {}", groupId, e);
            throw new GroupNotFoundException(groupId);
        }
    }

    public boolean isGroupLeader(UUID groupId, Long userId) {
        try {
            GroupDto group = getGroup(groupId);
            return group.getLeaderId() != null && group.getLeaderId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }
}
