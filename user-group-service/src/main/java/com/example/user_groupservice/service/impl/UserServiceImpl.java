package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.exception.*;
import com.example.user_groupservice.grpc.GetUserResponse;
import com.example.user_groupservice.grpc.GetUsersResponse;
import com.example.user_groupservice.grpc.IdentityServiceClient;
import com.example.user_groupservice.grpc.ListUsersResponse;
import com.example.user_groupservice.grpc.UpdateUserResponse;
import com.example.user_groupservice.mapper.UserGrpcMapper;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.service.UserService;
import com.example.user_groupservice.grpc.VerifyUserResponse;
import com.example.user_groupservice.grpc.GetUserRoleResponse;
import com.example.user_groupservice.grpc.UserRole;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of UserService.
 * Handles user profile operations with authorization checks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    
    private final IdentityServiceClient identityServiceClient;
    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    
    @Override
    public UserResponse getUserById(Long userId, Long actorId, List<String> actorRoles) {
        log.info("Getting user profile: userId={}, actorId={}", userId, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        // Fetch user from Identity Service via gRPC
        try {
            GetUserResponse user = identityServiceClient.getUser(userId);
            
            if (user.getDeleted()) {
                throw ResourceNotFoundException.userNotFound(userId);
            }

            return UserGrpcMapper.toUserResponse(user);
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when getting user: {}", e.getStatus());
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                throw ResourceNotFoundException.userNotFound(userId);
            }
            throw new RuntimeException("Failed to get user: " + e.getStatus().getCode());
        }
    }
    
    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request,
                                   Long actorId, List<String> actorRoles) {
        log.info("Updating user profile: userId={}, actorId={}", userId, actorId);
        
        boolean isAdmin = actorRoles.contains("ADMIN");
        boolean isLecturer = actorRoles.contains("LECTURER");
        boolean isSelf = userId.equals(actorId);
        
        // LECTURER is explicitly EXCLUDED from this API per spec (UC22-AUTH)
        if (isLecturer && !isAdmin) {
            log.warn("LECTURER attempted to update profile: actorId={}", actorId);
            throw ForbiddenException.lecturerCannotUpdateProfile();
        }
        
        // Authorization: ADMIN or SELF (STUDENT only)
        if (!isAdmin && !isSelf) {
            throw ForbiddenException.insufficientPermission();
        }
        
        // UC22 Implementation: Proxy to Identity Service via gRPC
        // Per spec: User & Group Service does not manage user data, 
        // but must forward update requests to Identity Service
        try {
            UpdateUserResponse grpcResponse = identityServiceClient.updateUser(
                    userId, request.getFullName());
            
            log.info("User profile updated via Identity Service: userId={}", userId);
            return UserGrpcMapper.toUserResponse(grpcResponse.getUser());


        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when updating user: {}", e.getStatus());
            return handleGrpcError(e, "update user");
        }
    }
    
    @Override
    public PageResponse<UserResponse> listUsers(int page, int size, 
                                                String status, String role) {
        log.info("Listing users: page={}, size={}, status={}, role={}", 
                page, size, status, role);
        
        // Get all users from Identity Service (no pagination in proto yet)
        // This is a workaround - ideally should add ListUsers RPC to proto
        try {
            // For now, get users by known IDs (1, 2, 3 from database)
            // TODO: Add proper ListUsers gRPC method with pagination to Identity Service
            ListUsersResponse response =
                identityServiceClient.listUsers(page, size, status, role);

            // Convert to UserResponse DTOs
            List<UserResponse> users = response.getUsersList().stream()
                    .map(UserGrpcMapper::toUserResponse)
                    .toList();
            
            // Apply filters if provided
            if (status != null && !status.isBlank()) {
                String statusUpper = status.toUpperCase();
                users = users.stream()
                        .filter(u -> u.getStatus().equals(statusUpper))

                        .toList();
            }
            
            if (role != null && !role.isBlank()) {
                String roleUpper = role.toUpperCase();
                users = users.stream()
                        .filter(u -> u.getRoles().contains(roleUpper))
                        .toList();
            }
            
            // Manual pagination
            int start = page * size;
            int end = Math.min(start + size, users.size());
            List<UserResponse> pageContent = (start < users.size()) 
                    ? users.subList(start, end) 
                    : List.of();
            
            return PageResponse.<UserResponse>builder()
                    .content(pageContent)
                    .page(page)
                    .size(size)
                    .totalElements(users.size())
                    .totalPages((int) Math.ceil((double) users.size() / size))
                    .build();
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when listing users: {}", e.getStatus());
            return handleGrpcError(e, "list users");
        }
    }
    
    @Override
    public UserGroupsResponse getUserGroups(Long userId, String semester,
                                            Long actorId, List<String> actorRoles) {
        log.info("Getting user groups: userId={}, semester={}, actorId={}", 
                userId, semester, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        // Verify user exists via gRPC
        try {
            VerifyUserResponse verification = identityServiceClient.verifyUserExists(userId);
            if (!verification.getExists()) {
                throw ResourceNotFoundException.userNotFound(userId);
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when verifying user: {}", e.getStatus());
            throw new RuntimeException("Failed to verify user: " + e.getStatus().getCode());
        }
        
        // Get user memberships
        List<UserGroup> memberships;
        if (semester != null && !semester.isBlank()) {
            memberships = userGroupRepository.findAllByUserIdAndSemester(userId, semester);
        } else {
            memberships = userGroupRepository.findAllByUserId(userId);
        }
        
        // Get all unique group IDs
        List<UUID> groupIds = memberships.stream()
                .map(UserGroup::getGroupId)
                .distinct()
                .toList();
        
        // Batch fetch all groups
        List<Group> groups = groupRepository.findAllById(groupIds);
        
        // Batch fetch all lecturer info
        List<Long> lecturerIds = groups.stream()
                .map(Group::getLecturerId)
                .distinct()
                .toList();
        
        GetUsersResponse lecturersResponse;
        try {
            lecturersResponse = identityServiceClient.getUsers(lecturerIds);
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when getting lecturers: {}", e.getStatus());
            throw new RuntimeException("Failed to get lecturer info: " + e.getStatus().getCode());
        }
        
        // Map group info with lecturer data
        List<UserGroupsResponse.GroupInfo> groupInfos = memberships.stream()
                .map(ug -> {
                    // Find the group for this membership
                    Group group = groups.stream()
                            .filter(g -> g.getId().equals(ug.getGroupId()))
                            .findFirst()
                            .orElse(null);
                    
                    if (group == null) {
                        return null; // Skip if group not found (should not happen)
                    }
                    
                    // Find lecturer info
                    Long lecturerId = group.getLecturerId();
                    GetUserResponse lecturer = lecturersResponse.getUsersList().stream()
                            .filter(u -> Long.parseLong(u.getUserId()) == lecturerId)
                            .findFirst()
                            .orElse(null);
                    
                    String lecturerName = (lecturer != null && !lecturer.getDeleted()) 
                            ? lecturer.getFullName() 
                            : "<Deleted User>";
                    
                    return UserGroupsResponse.GroupInfo.builder()
                            .groupId(ug.getGroupId())
                            .groupName(group.getGroupName())
                            .semester(group.getSemester())
                            .role(ug.getRole().name())
                            .lecturerName(lecturerName)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        
        return UserGroupsResponse.builder()
                .userId(userId)
                .groups(groupInfos)
                .build();
    }
    
    /**
     * Check authorization for viewing user profile.
     * Per spec (UC21-AUTH):
     * - ADMIN: can view all users
     * - LECTURER: can only view users with STUDENT role (if cannot verify, allow with warning)
     * - STUDENT: can only view self
     */
    private void checkGetUserAuthorization(Long targetUserId, Long actorId, 
                                           List<String> actorRoles) {
        boolean isAdmin = actorRoles.contains("ADMIN");
        boolean isLecturer = actorRoles.contains("LECTURER");
        boolean isSelf = targetUserId.equals(actorId);
        
        // ADMIN can view anyone
        if (isAdmin) {
            return;
        }
        
        // Self can always view own profile
        if (isSelf) {
            return;
        }
        
        // LECTURER can view students only
        // Per spec: Try to verify role first, if fail -> allow with warning (fallback)
        if (isLecturer) {
            try {
                GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(targetUserId);
                
                // Check if target is STUDENT
                if (roleResponse.getRole() == UserRole.STUDENT) {
                    log.debug("LECTURER {} viewing STUDENT {}: ALLOWED", actorId, targetUserId);
                    return;
                } else {
                    // Target is not STUDENT -> FORBIDDEN
                    log.warn("LECTURER {} attempted to view non-STUDENT user {}: role={}", 
                            actorId, targetUserId, roleResponse.getRole());
                    throw ForbiddenException.lecturerCanOnlyViewStudents();
                }
                
            } catch (StatusRuntimeException e) {
                // gRPC failure -> fallback behavior: ALLOW with warning
                log.warn("LECTURER {} viewing user {} - gRPC failed ({}). Allowing per spec (fallback).", 
                        actorId, targetUserId, e.getStatus().getCode());
                return;
            }
        }
        
        // STUDENT trying to view another user
        throw ForbiddenException.cannotAccessOtherUser();
    }
    
    /**
     * Handle gRPC errors with proper HTTP status mapping per API spec.
     */
    private <T> T handleGrpcError(StatusRuntimeException e, String operation) {
        Status.Code code = e.getStatus().getCode();
        
        switch (code) {
            case UNAVAILABLE:
                throw ServiceUnavailableException.identityServiceUnavailable();
            case DEADLINE_EXCEEDED:
                throw GatewayTimeoutException.identityServiceTimeout();
            case NOT_FOUND:
                throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found");
            case PERMISSION_DENIED:
                throw new ForbiddenException("FORBIDDEN", "Access denied");
            case INVALID_ARGUMENT:
                throw new BadRequestException("BAD_REQUEST", "Invalid request");
            default:
                throw new RuntimeException("Failed to " + operation + ": " + code);
        }
    }
}
