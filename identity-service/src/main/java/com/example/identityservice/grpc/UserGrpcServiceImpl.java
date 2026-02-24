package com.example.identityservice.grpc;

import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.grpc.UserGrpcServiceGrpc;
import com.example.identityservice.grpc.GetUserRequest;
import com.example.identityservice.grpc.GetUserResponse;
import com.example.identityservice.grpc.GetUserRoleRequest;
import com.example.identityservice.grpc.GetUserRoleResponse;
import com.example.identityservice.grpc.UserRole;
import com.example.identityservice.grpc.UserStatus;
import com.example.identityservice.grpc.VerifyUserRequest;
import com.example.identityservice.grpc.VerifyUserResponse;
import com.example.identityservice.grpc.GetUsersRequest;
import com.example.identityservice.grpc.GetUsersResponse;
import com.example.identityservice.grpc.UpdateUserRequest;
import com.example.identityservice.grpc.UpdateUserResponse;
import com.example.identityservice.grpc.ListUsersRequest;
import com.example.identityservice.grpc.ListUsersResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * gRPC Service Implementation for User Service.
 * Provides user data to other microservices (e.g., User-Group Service).
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserGrpcServiceImpl extends UserGrpcServiceGrpc.UserGrpcServiceImplBase {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
        try {
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC GetUser called: userId={}", userId);

            Optional<User> userOpt = userRepository.findByIdIgnoreDeleted(userId);
            
            if (userOpt.isEmpty()) {
                log.warn("User not found: userId={}", userId);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User not found")
                        .asRuntimeException());
                return;
            }

            User user = userOpt.get();
            GetUserResponse response = buildGetUserResponse(user);
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC GetUser completed: userId={}", userId);

        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", request.getUserId());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC GetUser: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getUserRole(GetUserRoleRequest request, StreamObserver<GetUserRoleResponse> responseObserver) {
        try {
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC GetUserRole called: userId={}", userId);

            Optional<User> userOpt = userRepository.findByIdIgnoreDeleted(userId);
            
            if (userOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User not found")
                        .asRuntimeException());
                return;
            }

            User user = userOpt.get();
            UserRole grpcRole = mapRole(user.getRole());
            
            GetUserRoleResponse response = GetUserRoleResponse.newBuilder()
                    .setRole(grpcRole)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NumberFormatException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC GetUserRole: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyUserExists(VerifyUserRequest request, StreamObserver<VerifyUserResponse> responseObserver) {
        try {
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC VerifyUserExists called: userId={}", userId);

            Optional<User> userOpt = userRepository.findByIdIgnoreDeleted(userId);
            
            boolean exists = userOpt.isPresent();
            boolean active = exists && userOpt.get().getStatus() == User.Status.ACTIVE;
            String message = exists ? 
                    (active ? "User exists and is active" : "User exists but not active") :
                    "User not found";
            
            VerifyUserResponse response = VerifyUserResponse.newBuilder()
                    .setExists(exists)
                    .setActive(active)
                    .setMessage(message)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NumberFormatException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC VerifyUserExists: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getUsers(GetUsersRequest request, StreamObserver<GetUsersResponse> responseObserver) {
        try {
            List<Long> userIds = request.getUserIdsList().stream()
                    .map(Long::parseLong)
                    .toList();
            
            log.info("gRPC GetUsers called: count={}", userIds.size());

            List<User> users = userRepository.findAllByIdInIgnoreDeleted(userIds);
            
            List<GetUserResponse> userResponses = users.stream()
                    .map(this::buildGetUserResponse)
                    .toList();
            
            GetUsersResponse response = GetUsersResponse.newBuilder()
                    .addAllUsers(userResponses)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (NumberFormatException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC GetUsers: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    @Override
    @Transactional
    public void updateUser(UpdateUserRequest request, StreamObserver<UpdateUserResponse> responseObserver) {
        try {
            Long userId = Long.parseLong(request.getUserId());
            log.info("gRPC UpdateUser called: userId={}, fullName={}", userId, request.getFullName());

            Optional<User> userOpt = userRepository.findByIdIgnoreDeleted(userId);
            
            if (userOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User not found")
                        .asRuntimeException());
                return;
            }

            User user = userOpt.get();
            user.setFullName(request.getFullName());
            user = userRepository.save(user);
            
            GetUserResponse userResponse = buildGetUserResponse(user);
            UpdateUserResponse response = UpdateUserResponse.newBuilder()
                    .setUser(userResponse)
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("gRPC UpdateUser completed: userId={}", userId);

        } catch (NumberFormatException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Error in gRPC UpdateUser: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    /**
     * Build GetUserResponse from User entity.
     */
    private GetUserResponse buildGetUserResponse(User user) {
        return GetUserResponse.newBuilder()
                .setUserId(String.valueOf(user.getId()))
                .setEmail(user.getEmail())
                .setFullName(user.getFullName())
                .setStatus(mapStatus(user.getStatus()))
                .setRole(mapRole(user.getRole()))
                .setDeleted(user.getDeletedAt() != null)
                .build();
    }

    /**
     * Map User.Status to gRPC UserStatus.
     */
    private UserStatus mapStatus(User.Status status) {
        return switch (status) {
            case ACTIVE -> UserStatus.ACTIVE;
            case LOCKED -> UserStatus.LOCKED;
        };
    }

    /**
     * Map User.Role to gRPC UserRole.
     */
    private UserRole mapRole(User.Role role) {
        return switch (role) {
            case ADMIN -> UserRole.ADMIN;
            case LECTURER -> UserRole.LECTURER;
            case STUDENT -> UserRole.STUDENT;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public void listUsers(ListUsersRequest request,
                          StreamObserver<ListUsersResponse> responseObserver) {
        try {
            int page = Math.max(0, request.getPage());
            int size = Math.max(1, Math.min(100, request.getSize())); // Max 100 items per page
            
            log.info("gRPC ListUsers called: page={}, size={}, status={}, role={}", 
                    page, size, request.getStatus(), request.getRole());

            // Parse optional filters
            User.Status status = parseStatus(request.getStatus());
            User.Role role = parseRole(request.getRole());

            // Query with pagination and filters
            Pageable pageable = PageRequest.of(page, size);
            Page<User> userPage = userRepository.findAllWithFilters(status, role, pageable);

            // Build response
            List<GetUserResponse> userResponses = userPage.getContent().stream()
                    .map(this::buildGetUserResponse)
                    .toList();

            ListUsersResponse response = ListUsersResponse.newBuilder()
                    .addAllUsers(userResponses)
                    .setTotalElements(userPage.getTotalElements())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
            log.info("gRPC ListUsers completed: returned {} users out of {} total", 
                    userResponses.size(), userPage.getTotalElements());

        } catch (Exception e) {
            log.error("Error in gRPC ListUsers: {}", e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error")
                    .asRuntimeException());
        }
    }

    /**
     * Parse status string to User.Status enum.
     * Returns null for empty/invalid values (means no filter).
     */
    private User.Status parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return User.Status.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status value: {}", statusStr);
            return null;
        }
    }

    /**
     * Parse role string to User.Role enum.
     * Returns null for empty/invalid values (means no filter).
     */
    private User.Role parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return null;
        }
        try {
            return User.Role.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role value: {}", roleStr);
            return null;
        }
    }

}
