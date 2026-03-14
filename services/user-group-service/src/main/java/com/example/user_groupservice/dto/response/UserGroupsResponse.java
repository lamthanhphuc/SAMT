package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
        private Long groupId;
        private String groupName;
        private Long semesterId;
        private String semesterCode;
        private String role;
        private String lecturerName;
    }
}
