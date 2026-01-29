package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for group details including members.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailResponse {
    
    private UUID id;
    private String groupName;
    private String semester;
    private LecturerInfo lecturer;
    private List<MemberInfo> members;
    private int memberCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LecturerInfo {
        private UUID id;
        private String fullName;
        private String email;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private UUID userId;
        private String fullName;
        private String email;
        private String role;
    }
    
    /**
     * Create GroupDetailResponse from Group entity and members.
     */
    public static GroupDetailResponse from(Group group, List<MemberInfo> members) {
        return GroupDetailResponse.builder()
                .id(group.getId())
                .groupName(group.getGroupName())
                .semester(group.getSemester())
                .lecturer(LecturerInfo.builder()
                        .id(group.getLecturer().getId())
                        .fullName(group.getLecturer().getFullName())
                        .email(group.getLecturer().getEmail())
                        .build())
                .members(members)
                .memberCount(members.size())
                .build();
    }
}
