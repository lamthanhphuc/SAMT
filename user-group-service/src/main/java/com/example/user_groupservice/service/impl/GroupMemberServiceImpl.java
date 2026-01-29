package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.request.AddMemberRequest;
import com.example.user_groupservice.dto.request.AssignRoleRequest;
import com.example.user_groupservice.dto.response.GroupMembersResponse;
import com.example.user_groupservice.dto.response.MemberResponse;
import com.example.user_groupservice.entity.*;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserGroupRepository;
import com.example.user_groupservice.repository.UserRepository;
import com.example.user_groupservice.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
        log.info("Adding member to group: groupId={}, userId={}, isLeader={}", 
                groupId, request.getUserId(), request.getIsLeader());
        
        // Validate user exists and is active
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> ResourceNotFoundException.userNotFound(request.getUserId()));
        
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ConflictException.userInactive(user.getId());
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
                .user(user)
                .group(group)
                .role(role)
                .build();
        
        userGroupRepository.save(membership);
        log.info("Member added successfully: groupId={}, userId={}, role={}", 
                groupId, request.getUserId(), role);
        
        return MemberResponse.from(membership);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberResponse assignRole(UUID groupId, UUID userId, AssignRoleRequest request) {
        log.info("Assigning role: groupId={}, userId={}, role={}", 
                groupId, userId, request.getRole());
        
        // Validate group exists - use findById() not existsById() per spec (soft delete caveat)
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserGroup membership = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> ResourceNotFoundException.memberNotFound(userId, groupId));
        
        GroupRole newRole = GroupRole.valueOf(request.getRole());
        
        // If assigning LEADER, demote old leader first with PESSIMISTIC LOCK
        // Per spec (UC25-LOCK): Use pessimistic lock to prevent race conditions
        if (newRole == GroupRole.LEADER) {
            Optional<UserGroup> existingLeader = userGroupRepository.findLeaderByGroupIdWithLock(groupId);
            
            if (existingLeader.isPresent()) {
                UserGroup oldLeader = existingLeader.get();
                // Only demote if it's a different user
                if (!oldLeader.getUser().getId().equals(userId)) {
                    log.info("Demoting old leader: groupId={}, oldLeaderId={}", 
                            groupId, oldLeader.getUser().getId());
                    oldLeader.demoteToMember();
                    userGroupRepository.save(oldLeader);
                }
            }
        }
        
        // Assign new role
        membership.setRole(newRole);
        UserGroup savedMembership = userGroupRepository.save(membership);
        
        log.info("Role assigned successfully: groupId={}, userId={}, role={}", 
                groupId, userId, newRole);
        
        return MemberResponse.from(savedMembership);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(UUID groupId, UUID userId) {
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
        
        List<GroupMembersResponse.MemberInfo> members = memberships.stream()
                .map(ug -> GroupMembersResponse.MemberInfo.builder()
                        .userId(ug.getUser().getId())
                        .fullName(ug.getUser().getFullName())
                        .email(ug.getUser().getEmail())
                        .role(ug.getRole().name())
                        .build())
                .collect(Collectors.toList());
        
        return GroupMembersResponse.builder()
                .groupId(groupId)
                .groupName(group.getGroupName())
                .members(members)
                .totalMembers(members.size())
                .build();
    }
}
