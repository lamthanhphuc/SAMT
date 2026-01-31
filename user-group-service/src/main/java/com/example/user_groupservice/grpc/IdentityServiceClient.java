package com.example.user_groupservice.grpc;

import com.samt.identity.grpc.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for Identity Service integration.
 * Provides user data fetching and validation methods.
 */
@Service
@Slf4j
public class IdentityServiceClient {
    
    private final UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userStub;
    private final long deadlineSeconds;
    
    public IdentityServiceClient(
            UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userStub,
            long grpcDeadlineSeconds) {
        this.userStub = userStub;
        this.deadlineSeconds = grpcDeadlineSeconds;
    }
    
    /**
     * Get user by ID from Identity Service.
     * 
     * @param userId User ID
     * @return GetUserResponse with user data
     * @throws StatusRuntimeException if gRPC call fails
     */
    public GetUserResponse getUser(Long userId) {
        log.debug("Fetching user from Identity Service: {}", userId);
        
        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();
            
            // Apply deadline per-call to avoid deadline expiry
            GetUserResponse response = userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUser(request);
            log.debug("User fetched successfully: {}", userId);
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch user {}: {}", userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Get user's system role from Identity Service.
     * 
     * @param userId User ID
     * @return GetUserRoleResponse with role
     * @throws StatusRuntimeException if gRPC call fails
     */
    public GetUserRoleResponse getUserRole(Long userId) {
        log.debug("Fetching user role from Identity Service: {}", userId);
        
        try {
            GetUserRoleRequest request = GetUserRoleRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();
            
            GetUserRoleResponse response = userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUserRole(request);
            log.debug("User role fetched: {} -> {}", userId, response.getRole());
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch user role {}: {}", userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Verify user exists and is active in Identity Service.
     * 
     * @param userId User ID
     * @return VerifyUserResponse with exists/active flags
     * @throws StatusRuntimeException if gRPC call fails
     */
    public VerifyUserResponse verifyUserExists(Long userId) {
        log.debug("Verifying user exists: {}", userId);
        
        try {
            VerifyUserRequest request = VerifyUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();
            
            VerifyUserResponse response = userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .verifyUserExists(request);
            log.debug("User verification: {} -> exists={}, active={}", 
                    userId, response.getExists(), response.getActive());
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to verify user {}: {}", userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Batch get users from Identity Service.
     * Optimizes performance by reducing N+1 gRPC calls.
     * 
     * @param userIds List of user IDs
     * @return GetUsersResponse with list of users
     * @throws StatusRuntimeException if gRPC call fails
     */
    public GetUsersResponse getUsers(List<Long> userIds) {
        log.debug("Batch fetching {} users from Identity Service", userIds.size());
        
        try {
            List<String> userIdStrings = userIds.stream()
                    .map(Object::toString)
                    .toList();
            
            GetUsersRequest request = GetUsersRequest.newBuilder()
                    .addAllUserIds(userIdStrings)
                    .build();
            
            GetUsersResponse response = userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUsers(request);
            log.debug("Batch fetched {} users", response.getUsersCount());
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to batch fetch users: {}", e.getStatus());
            throw e;
        }
    }
    
    /**
     * Update user profile via Identity Service (UC22 - proxy pattern).
     * 
     * @param userId User ID
     * @param fullName New full name
     * @return UpdateUserResponse with updated user
     * @throws StatusRuntimeException if gRPC call fails
     */
    public UpdateUserResponse updateUser(Long userId, String fullName) {
        log.debug("Updating user profile via Identity Service: {}", userId);
        
        try {
            UpdateUserRequest request = UpdateUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setFullName(fullName)
                    .build();
            
            UpdateUserResponse response = userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .updateUser(request);
            log.debug("User profile updated successfully: {}", userId);
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("Failed to update user {}: {}", userId, e.getStatus());
            throw e;
        }
    }
}
