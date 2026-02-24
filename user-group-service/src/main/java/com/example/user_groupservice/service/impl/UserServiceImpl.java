package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.Semester;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.exception.*;
import com.example.user_groupservice.grpc.GetUserResponse;
import com.example.user_groupservice.grpc.GetUsersResponse;
import com.example.user_groupservice.grpc.IdentityServiceClient;
import com.example.user_groupservice.grpc.ListUsersResponse;
import com.example.user_groupservice.grpc.UpdateUserResponse;
import com.example.user_groupservice.mapper.UserGrpcMapper;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.SemesterRepository;
import com.example.user_groupservice.repository.UserSemesterMembershipRepository;
import com.example.user_groupservice.service.UserService;
import com.example.user_groupservice.grpc.VerifyUserResponse;
import com.example.user_groupservice.grpc.GetUserRoleResponse;
import com.example.user_groupservice.grpc.UserRole;
import com.example.user_groupservice.grpc.GrpcExceptionHandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.grpc.StatusRuntimeException;


import java.util.List;
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
    private final UserSemesterMembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final SemesterRepository semesterRepository;
    private final GrpcExceptionHandler grpcExceptionHandler;
    
    @Override
    public UserResponse getUserById(Long userId, Long actorId, List<String> actorRoles) {
        log.info("Getting user profile: userId={}, actorId={}", userId, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        // Fetch user from Identity Service via gRPC
        GetUserResponse user = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUser(userId),
                "getUser");
        
        if (user.getDeleted()) {
            throw ResourceNotFoundException.userNotFound(userId);
        }

        return UserGrpcMapper.toUserResponse(user);
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
        UpdateUserResponse grpcResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.updateUser(userId, request.getFullName()),
                "updateUser");
        
        log.info("User profile updated via Identity Service: userId={}", userId);
        return UserGrpcMapper.toUserResponse(grpcResponse.getUser());
    }

    @Override
    public PageResponse<UserResponse> listUsers(int page, int size,
                                                String status, String role) {

        log.info("Listing users: page={}, size={}, status={}, role={}",
                page, size, status, role);

        // Gọi Identity Service thông qua GrpcExceptionHandler
        ListUsersResponse response = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.listUsers(page, size, status, role),
                "listUsers"
        );

        // Convert sang DTO
        List<UserResponse> users = response.getUsersList().stream()
                .map(UserGrpcMapper::toUserResponse)
                .toList();

        // Filter theo status (nếu có)
        if (status != null && !status.isBlank()) {
            String statusUpper = status.toUpperCase();
            users = users.stream()
                    .filter(u -> u.getStatus().equalsIgnoreCase(statusUpper))
                    .toList();
        }

        // Filter theo role (nếu có)
        if (role != null && !role.isBlank()) {
            String roleUpper = role.toUpperCase();
            users = users.stream()
                    .filter(u -> u.getRoles().contains(roleUpper))
                    .toList();
        }

        // Manual pagination
        int start = page * size;
        int end = Math.min(start + size, users.size());

        List<UserResponse> pageContent =
                (start < users.size()) ? users.subList(start, end) : List.of();

        return PageResponse.<UserResponse>builder()
                .content(pageContent)
                .page(page)
                .size(size)
                .totalElements(users.size())
                .totalPages(size == 0 ? 0 :
                        (int) Math.ceil((double) users.size() / size))
                .build();
    }


    @Override
    public UserGroupsResponse getUserGroups(Long userId, String semester,
                                            Long actorId, List<String> actorRoles) {
        log.info("Getting user groups: userId={}, semester={}, actorId={}", 
                userId, semester, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        // Verify user exists via gRPC
        VerifyUserResponse verification = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.verifyUserExists(userId),
                "verifyUserExists");
        if (!verification.getExists()) {
            throw ResourceNotFoundException.userNotFound(userId);
        }
        
        // Get user memberships
        List<UserSemesterMembership> memberships = membershipRepository.findAllByUserId(userId);
        
        // Get all unique group IDs
        List<Long> groupIds = memberships.stream()
                .map(UserSemesterMembership::getGroupId)
                .distinct()
                .toList();
        
        // Batch fetch all groups
        List<Group> groups = groupRepository.findAllById(groupIds);
        
        // Batch fetch all lecturer info
        List<Long> lecturerIds = groups.stream()
                .map(Group::getLecturerId)
                .distinct()
                .toList();
        
        GetUsersResponse lecturersResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUsers(lecturerIds),
                "getUsers[getUserGroups]");
        
        // Map group info with lecturer data
        List<UserGroupsResponse.GroupInfo> groupInfos = memberships.stream()
                .filter(m -> semester == null || semester.isBlank() || 
                        groups.stream().anyMatch(g -> g.getId().equals(m.getGroupId()) && g.getSemesterId().equals(m.getId().getSemesterId())))
                .map(m -> {
                    // Find the group for this membership
                    Group group = groups.stream()
                            .filter(g -> g.getId().equals(m.getGroupId()))
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
                    
                    // Resolve semester code from local database
                    String semesterCode = resolveSemesterCode(m.getId().getSemesterId());
                    
                    return UserGroupsResponse.GroupInfo.builder()
                            .groupId(m.getGroupId())
                            .groupName(group.getGroupName())
                            .semesterId(m.getId().getSemesterId())
                            .semesterCode(semesterCode)
                            .role(m.getGroupRole().name())
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
     * Resolve semester code from local database.
     * 
     * @param semesterId Semester ID
     * @return Semester code (e.g., "SPRING2025")
     * @throws ResourceNotFoundException if semester not found
     */
    private String resolveSemesterCode(Long semesterId) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> ResourceNotFoundException.semesterNotFound(semesterId));
        return semester.getSemesterCode();
    }
    
    /**
     * Check authorization for viewing user profile.
     * Per spec (UC21-AUTH):
     * - ADMIN: can view all users
     * - LECTURER: can only view users with STUDENT role (if cannot verify, allow with warning)
     * - STUDENT: can only view self
     * 
     * PRIVACY ENHANCEMENT (Issue 6.1 Fix):
     * - LECTURER can only view STUDENTS they supervise (not all students in system)
     * - This prevents lecturers from accessing student data outside their responsibility
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
        
        // LECTURER can view students only (with additional privacy check)
        // Per spec: Try to verify role first, if fail -> allow with warning (fallback)
        if (isLecturer) {
            try {
                GetUserRoleResponse roleResponse = grpcExceptionHandler.handleGrpcCall(
                        () -> identityServiceClient.getUserRole(targetUserId),
                        "getUserRole[checkAuth]");
                
                // Check if target is STUDENT
                if (roleResponse.getRole() == UserRole.STUDENT) {
                    // Privacy check: Verify lecturer supervises this student
                    boolean lecturerSupervisesStudent = groupRepository
                            .existsByLecturerAndStudent(actorId, targetUserId);
                    
                    if (!lecturerSupervisesStudent) {
                        log.warn("LECTURER {} attempted to view STUDENT {} who is not in their groups", 
                                actorId, targetUserId);
                        throw ForbiddenException.lecturerCanOnlyViewSupervisedStudents();
                    }
                    
                    log.debug("LECTURER {} viewing supervised STUDENT {}: ALLOWED", actorId, targetUserId);
                    return;
                } else {
                    // Target is not STUDENT -> FORBIDDEN
                    log.warn("LECTURER {} attempted to view non-STUDENT user {}: role={}", 
                            actorId, targetUserId, roleResponse.getRole());
                    throw ForbiddenException.lecturerCanOnlyViewStudents();
                }
                
            } catch (ResourceNotFoundException | ForbiddenException | ConflictException | 
                     ServiceUnavailableException | GatewayTimeoutException | BadRequestException | 
                     UnauthorizedException e) {
                // Business exceptions should be thrown normally
                throw e;
            } catch (RuntimeException e) {
                // gRPC failure -> fallback behavior: ALLOW with warning
                log.warn("LECTURER {} viewing user {} - gRPC failed ({}). Allowing per spec (fallback).", 
                        actorId, targetUserId, e.getMessage());
                return;
            }
        }
        
        // STUDENT trying to view another user
        throw ForbiddenException.cannotAccessOtherUser();
    }
}
