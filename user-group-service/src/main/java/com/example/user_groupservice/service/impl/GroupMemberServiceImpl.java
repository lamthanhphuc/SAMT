package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.AddMemberRequest;
import com.example.user_groupservice.dto.request.AssignRoleRequest;
import com.example.user_groupservice.dto.response.GroupMembersResponse;
import com.example.user_groupservice.dto.response.MemberResponse;
import com.example.user_groupservice.entity.*;
import com.example.user_groupservice.exception.*;
import com.example.user_groupservice.grpc.IdentityServiceClient;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.service.GroupMemberService;
import com.samt.identity.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of GroupMemberService.
 * Handles group membership operations with business rule enforcement.
 * 
 * Key business rules:
 * - User can only be in ONE group per semester
 * - Each group can have only ONE LEADER
 * - Cannot remove LEADER if group has other members
 * - When assigning new LEADER, old LEADER is auto-demoted
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    private final IdentityServiceClient identityServiceClient;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
        log.info("UC24 - Adding member to group: groupId={}, userId={}, isLeader={}", 
                groupId, request.getUserId(), request.getIsLeader());
        
        // Validate user exists and is active via gRPC
        try {
            VerifyUserResponse verification = identityServiceClient.verifyUserExists(
                    request.getUserId());
            
            if (!verification.getExists()) {
                throw ResourceNotFoundException.userNotFound(request.getUserId());
            }
            
            if (!verification.getActive()) {
                throw ConflictException.userInactive(request.getUserId());
            }
            
            // UC24 Validation: Only STUDENT can be added to groups (BR-UG-009)
            GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(
                    request.getUserId());
            
            if (roleResponse.getRole() != UserRole.STUDENT) {
                log.warn("UC24 - Attempted to add non-STUDENT to group: userId={}, role={}", 
                        request.getUserId(), roleResponse.getRole());
                throw BadRequestException.invalidRole(
                        roleResponse.getRole().name(), "STUDENT");
            }
            
            log.debug("UC24 - User validated as STUDENT: userId={}", request.getUserId());
            
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when validating user: {}", e.getStatus());
            return handleGrpcError(e, "validate user");
        }
        
        // Validate group exists
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Check user is not already in this group
        if (userGroupRepository.existsByUserIdAndGroupId(request.getUserId(), groupId)) {
            throw ConflictException.userAlreadyInGroup(request.getUserId(), groupId);
        }
        
        // Check user is not already in a group for this semester
        if (userGroupRepository.existsByUserIdAndSemester(
                request.getUserId(), group.getSemester())) {
            throw ConflictException.userAlreadyInGroupSameSemester(
                    request.getUserId(), group.getSemester());
        }
        
        // Determine role
        GroupRole role = Boolean.TRUE.equals(request.getIsLeader()) 
                ? GroupRole.LEADER 
                : GroupRole.MEMBER;
        
        // If isLeader=true, check no existing leader
        if (role == GroupRole.LEADER) {
            if (userGroupRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
                throw ConflictException.leaderAlreadyExists(groupId);
            }
        }
        
        // Create membership
        UserGroup membership = UserGroup.builder()
                .userId(request.getUserId())
                .groupId(groupId)
                .role(role)
                .build();
        
        userGroupRepository.save(membership);
        log.info("UC24 - Member added successfully: groupId={}, userId={}, role={}", 
                groupId, request.getUserId(), role);
        
        // Fetch user info from Identity Service for response
        GetUserResponse userInfo = identityServiceClient.getUser(request.getUserId());
        
        return MemberResponse.builder()
                .userId(request.getUserId())
                .groupId(groupId)
                .fullName(userInfo.getFullName())
                .email(userInfo.getEmail())
                .role(role.name())
                .build();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
    public MemberResponse assignRole(UUID groupId, Long userId, AssignRoleRequest request) {
        log.info("UC25 - Assigning role: groupId={}, userId={}, role={}", 
                groupId, userId, request.getRole());
        
        // Validate group exists - use findById() not existsById() per spec (soft delete caveat)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserGroup membership = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> ResourceNotFoundException.memberNotFound(userId, groupId));
        
        GroupRole newRole = GroupRole.valueOf(request.getRole());
        
        // UC25: If assigning LEADER, demote old leader first with PESSIMISTIC LOCK
        // Per spec (UC25-LOCK): Use pessimistic lock + SERIALIZABLE isolation to prevent race conditions
        if (newRole == GroupRole.LEADER) {
            Optional<UserGroup> existingLeader = userGroupRepository.findLeaderByGroupIdWithLock(groupId);
            
            if (existingLeader.isPresent()) {
                UserGroup oldLeader = existingLeader.get();
                // Only demote if it's a different user
                if (!oldLeader.getUserId().equals(userId)) {
                    log.info("UC25 - Demoting old leader: groupId={}, oldLeaderId={}", 
                            groupId, oldLeader.getUserId());
                    oldLeader.demoteToMember();
                    userGroupRepository.save(oldLeader);
                }
            }
        }
        
        // Assign new role
        membership.setRole(newRole);
        userGroupRepository.save(membership);
        
        log.info("UC25 - Role assigned successfully: groupId={}, userId={}, role={}", 
                groupId, userId, newRole);
        
        // Fetch user info for response
        GetUserResponse userInfo = identityServiceClient.getUser(userId);
        
        return MemberResponse.builder()
                .userId(userId)
                .groupId(groupId)
                .fullName(userInfo.getFullName())
                .email(userInfo.getEmail())
                .role(newRole.name())
                .build();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(UUID groupId, Long userId) {
        log.info("Removing member from group: groupId={}, userId={}", groupId, userId);
        
        // Validate group exists - use findById() not existsById() per spec (soft delete caveat)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserGroup membership = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> ResourceNotFoundException.memberNotFound(userId, groupId));
        
        // If removing leader, check no other members
        if (membership.isLeader()) {
            long memberCount = userGroupRepository.countMembersByGroupId(groupId);
            if (memberCount > 0) {
                throw ConflictException.cannotRemoveLeader();
            }
        }
        
        // Soft delete the membership
        membership.softDelete();
        userGroupRepository.save(membership);
        
        log.info("Member removed successfully: groupId={}, userId={}", groupId, userId);
    }
    
    @Override
    public GroupMembersResponse getGroupMembers(UUID groupId, GroupRole role) {
        log.info("Getting group members: groupId={}, role={}", groupId, role);
        
        // Validate group exists
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        List<UserGroup> memberships;
        if (role != null) {
            memberships = userGroupRepository.findAllByGroupIdAndRole(groupId, role);
        } else {
            memberships = userGroupRepository.findAllByGroupId(groupId);
        }
        
        // Batch fetch all user info
        List<Long> userIds = memberships.stream()
                .map(UserGroup::getUserId)
                .toList();
        
        GetUsersResponse usersResponse;
        try {
            usersResponse = identityServiceClient.getUsers(userIds);
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed when getting users: {}", e.getStatus());
            throw new RuntimeException("Failed to get user info: " + e.getStatus().getCode());
        }
        
        // Map user info with deleted user handling
        List<GroupMembersResponse.MemberInfo> members = memberships.stream()
                .map(ug -> {
                    GetUserResponse userInfo = usersResponse.getUsersList().stream()
                            .filter(u -> Long.parseLong(u.getUserId()) == ug.getUserId())
                            .findFirst()
                            .orElse(null);
                    
                    return GroupMembersResponse.MemberInfo.builder()
                            .userId(ug.getUserId())
                            .fullName(userInfo != null ? userInfo.getFullName() : "<Deleted User>")
                            .email(userInfo != null && !userInfo.getDeleted() ? userInfo.getEmail() : null)
                            .role(ug.getRole().name())
                            .build();
                })
                .collect(Collectors.toList());
        
        return GroupMembersResponse.builder()
                .groupId(groupId)
                .groupName(group.getGroupName())
                .members(members)
                .totalMembers(members.size())
                .build();
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
