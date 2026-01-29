package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.UserGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for member operations (add, assign role).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    
    private UUID userId;
    private UUID groupId;
    private String fullName;
    private String email;
    private String role;
    
    /**
     * Create MemberResponse from UserGroup entity.
     */
    public static MemberResponse from(UserGroup membership) {
        return MemberResponse.builder()
                .userId(membership.getUser().getId())
                .groupId(membership.getGroup().getId())
                .fullName(membership.getUser().getFullName())
                .email(membership.getUser().getEmail())
                .role(membership.getRole().name())
                .build();
    }
}
