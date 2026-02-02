package com.example.user_groupservice.grpc;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * gRPC client for Identity Service integration.
 * Provides user data fetching and validation methods.
 */
@Service
@Slf4j
public class IdentityServiceClient {

    @GrpcClient("identity-service")
    private UserGrpcServiceGrpc.UserGrpcServiceBlockingStub userStub;

    /**
     * Per-call gRPC deadline in seconds.
     * Configurable via application.yml
     */
    @Value("${grpc.client.identity-service.deadline-seconds:3}")
    private long deadlineSeconds;

    /**
     * Get user by ID from Identity Service.
     */
    public GetUserResponse getUser(Long userId) {
        log.debug("Fetching user from Identity Service: {}", userId);

        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();

            return userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUser(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch user {}: {}", userId, e.getStatus());
            throw e;
        }
    }

    /**
     * Get user's system role from Identity Service.
     */
    public GetUserRoleResponse getUserRole(Long userId) {
        log.debug("Fetching user role from Identity Service: {}", userId);

        try {
            GetUserRoleRequest request = GetUserRoleRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();

            return userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUserRole(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch user role {}: {}", userId, e.getStatus());
            throw e;
        }
    }

    /**
     * Verify user exists and is active in Identity Service.
     */
    public VerifyUserResponse verifyUserExists(Long userId) {
        log.debug("Verifying user exists: {}", userId);

        try {
            VerifyUserRequest request = VerifyUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build();

            return userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .verifyUserExists(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to verify user {}: {}", userId, e.getStatus());
            throw e;
        }
    }

    /**
     * Batch get users from Identity Service.
     * Avoids N+1 gRPC calls.
     */
    public GetUsersResponse getUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return GetUsersResponse.newBuilder().build();
        }

        log.debug("Batch fetching {} users from Identity Service", userIds.size());

        try {
            List<String> userIdStrings = userIds.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());

            GetUsersRequest request = GetUsersRequest.newBuilder()
                    .addAllUserIds(userIdStrings)
                    .build();

            return userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .getUsers(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to batch fetch users: {}", e.getStatus());
            throw e;
        }
    }

    /**
     * Update user profile via Identity Service (UC22 - proxy pattern).
     */
    public UpdateUserResponse updateUser(Long userId, String fullName) {
        log.debug("Updating user profile via Identity Service: {}", userId);

        try {
            UpdateUserRequest request = UpdateUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setFullName(fullName)
                    .build();

            return userStub
                    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                    .updateUser(request);

        } catch (StatusRuntimeException e) {
            log.error("Failed to update user {}: {}", userId, e.getStatus());
            throw e;
        }
    }
}
