package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for user's groups.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupsResponse {
    
    private Long userId;
    private List<GroupInfo> groups;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupInfo {
        private UUID groupId;
        private String groupName;
        private String semester;
        private String role;
        private String lecturerName;
    }
}
