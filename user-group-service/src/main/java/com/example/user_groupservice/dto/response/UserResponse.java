package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.User;
import com.example.user_groupservice.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    private UUID id;
    private String email;
    private String fullName;
    private UserStatus status;
    private List<String> roles;
    
    /**
     * Create UserResponse from User entity.
     * Note: roles must be provided externally (from JWT or identity-service).
     */
    public static UserResponse from(User user, List<String> roles) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roles(roles)
                .build();
    }
}
