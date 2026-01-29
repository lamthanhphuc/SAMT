package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.entity.User;
import com.example.user_groupservice.entity.UserGroup;
import com.example.user_groupservice.entity.UserStatus;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ForbiddenException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.repository.UserRepository;
import com.example.user_groupservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
    
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    
    @Override
    public UserResponse getUserById(UUID userId, UUID actorId, List<String> actorRoles) {
        log.info("Getting user profile: userId={}, actorId={}", userId, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(userId));
        
        // Note: In a real system, roles would come from identity-service
        // For now, we return an empty list as roles are in JWT
        return UserResponse.from(user, Collections.emptyList());
    }
    
    @Override
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request,
                                   UUID actorId, List<String> actorRoles) {
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
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(userId));
        
        // Check user is active
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ConflictException.userInactive(userId);
        }
        
        // Update fields
        user.setFullName(request.getFullName().trim());
        
        User savedUser = userRepository.save(user);
        log.info("User profile updated successfully: userId={}", userId);
        
        return UserResponse.from(savedUser, Collections.emptyList());
    }
    
    @Override
    public PageResponse<UserResponse> listUsers(int page, int size, 
                                                UserStatus status, String role) {
        log.info("Listing users: page={}, size={}, status={}, role={}", 
                page, size, status, role);
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("fullName").ascending());
        
        Page<User> userPage;
        if (status != null) {
            userPage = userRepository.findByStatus(status, pageRequest);
        } else {
            userPage = userRepository.findAll(pageRequest);
        }
        
        List<UserResponse> users = userPage.getContent().stream()
                .map(user -> UserResponse.from(user, Collections.emptyList()))
                .collect(Collectors.toList());
        
        return PageResponse.of(
                users,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }
    
    @Override
    public UserGroupsResponse getUserGroups(UUID userId, String semester,
                                            UUID actorId, List<String> actorRoles) {
        log.info("Getting user groups: userId={}, semester={}, actorId={}", 
                userId, semester, actorId);
        
        // Authorization check
        checkGetUserAuthorization(userId, actorId, actorRoles);
        
        // Verify user exists - use findById() not existsById() per spec (soft delete caveat)
        if (userRepository.findById(userId).isEmpty()) {
            throw ResourceNotFoundException.userNotFound(userId);
        }
        
        List<UserGroup> memberships;
        if (semester != null && !semester.isBlank()) {
            memberships = userGroupRepository.findAllByUserIdAndSemester(userId, semester);
        } else {
            memberships = userGroupRepository.findAllByUserId(userId);
        }
        
        List<UserGroupsResponse.GroupInfo> groups = memberships.stream()
                .map(ug -> UserGroupsResponse.GroupInfo.builder()
                        .groupId(ug.getGroup().getId())
                        .groupName(ug.getGroup().getGroupName())
                        .semester(ug.getGroup().getSemester())
                        .role(ug.getRole().name())
                        .lecturerName(ug.getGroup().getLecturer().getFullName())
                        .build())
                .collect(Collectors.toList());
        
        return UserGroupsResponse.builder()
                .userId(userId)
                .groups(groups)
                .build();
    }
    
    /**
     * Check authorization for viewing user profile.
     * Per spec (UC21-AUTH):
     * - ADMIN: can view all users
     * - LECTURER: can only view users with STUDENT role (if cannot verify, allow with warning)
     * - STUDENT: can only view self
     */
    private void checkGetUserAuthorization(UUID targetUserId, UUID actorId, 
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
        // Per spec: In cross-service scenarios where target role cannot be verified,
        // the request is allowed with a warning log
        if (isLecturer) {
            log.warn("LECTURER {} viewing user {} - cannot verify target is STUDENT (cross-service). Allowing per spec.", 
                    actorId, targetUserId);
            return;
        }
        
        // STUDENT trying to view another user
        throw ForbiddenException.cannotAccessOtherUser();
    }
}
