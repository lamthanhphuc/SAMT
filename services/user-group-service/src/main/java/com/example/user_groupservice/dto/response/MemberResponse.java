package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.GroupRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for member operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    
    private Long userId;
    private Long groupId;
    private Long semesterId;
    private GroupRole groupRole;
    private Instant joinedAt;
    private Instant updatedAt;
}
