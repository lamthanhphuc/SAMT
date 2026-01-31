package com.example.user_groupservice.dto.response;

import com.example.user_groupservice.entity.UserStatus;
import com.samt.identity.grpc.GetUserResponse;
import com.samt.identity.grpc.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for user profile.
 * Constructed from gRPC GetUserResponse (Identity Service).
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
     * Create UserResponse from gRPC GetUserResponse.
     */
    public static UserResponse fromGrpc(GetUserResponse grpcUser) {
        return UserResponse.builder()
                .id(UUID.fromString(grpcUser.getUserId()))
                .email(grpcUser.getEmail())
                .fullName(grpcUser.getFullName())
                .status(mapStatus(grpcUser.getStatus()))
                .roles(List.of(grpcUser.getRole().name()))
                .build();
    }
    
    private static UserStatus mapStatus(com.samt.identity.grpc.UserStatus grpcStatus) {
        return switch (grpcStatus) {
            case ACTIVE -> UserStatus.ACTIVE;
            case INACTIVE, LOCKED -> UserStatus.INACTIVE;
            default -> UserStatus.INACTIVE;
        };
    }
}
