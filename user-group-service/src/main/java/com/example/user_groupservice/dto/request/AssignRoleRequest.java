package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for assigning a role to a group member.
 * Role must be either LEADER or MEMBER.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequest {
    
    @NotNull(message = "Role is required")
    @Pattern(regexp = "^(LEADER|MEMBER)$", message = "Role must be LEADER or MEMBER")
    private String role;
}
