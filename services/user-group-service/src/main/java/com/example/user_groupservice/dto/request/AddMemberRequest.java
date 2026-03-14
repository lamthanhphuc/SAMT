package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding a member to a group.
 * All members are added with MEMBER role by default.
 * Use the promote endpoint to assign LEADER role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {
    
    @NotNull(message = "User ID is required")
    private Long userId;
}
