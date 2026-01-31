package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for group members list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMembersResponse {
    
    private UUID groupId;
    private String groupName;
    private List<MemberInfo> members;
    private int totalMembers;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private Long userId;
        private String fullName;
        private String email;
        private String role;
    }
}
