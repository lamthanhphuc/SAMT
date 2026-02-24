package com.example.user_groupservice.service.impl;

import com.example.user_groupservice.dto.response.MemberResponse;
import com.example.user_groupservice.entity.Group;
import com.example.user_groupservice.entity.GroupRole;
import com.example.user_groupservice.entity.UserSemesterMembership;
import com.example.user_groupservice.entity.UserSemesterMembershipId;
import com.example.user_groupservice.exception.BadRequestException;
import com.example.user_groupservice.exception.ConflictException;
import com.example.user_groupservice.exception.ResourceNotFoundException;
import com.example.user_groupservice.grpc.GetUserRoleResponse;
import com.example.user_groupservice.grpc.GrpcExceptionHandler;
import com.example.user_groupservice.grpc.ResilientIdentityServiceClient;
import com.example.user_groupservice.grpc.UserRole;
import com.example.user_groupservice.repository.GroupRepository;
import com.example.user_groupservice.repository.UserSemesterMembershipRepository;
import com.example.user_groupservice.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of GroupMemberService using UserSemesterMembership entity
 * Business Rules:
 * - PK (user_id, semester_id) enforces one group per semester
 * - DB unique index enforces one LEADER per group
 * - Soft delete pattern used throughout
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private final UserSemesterMembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final ResilientIdentityServiceClient identityServiceClient;
    private final GrpcExceptionHandler grpcExceptionHandler;
    
    @Override
    @Transactional
    public MemberResponse addMember(Long groupId, Long userId) {
        log.info("UC24 - Adding member to group: groupId={}, userId={}", groupId, userId);
        
        // Validate group exists
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "GROUP_NOT_FOUND",
                "Group not found: " + groupId
            ));
        
        // Validate user exists and is ACTIVE via gRPC
        grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.verifyUserExists(userId),
                "verifyUserExists[addMember]");
        
        // Validate user has STUDENT role (BR-UG-007: Only STUDENT can join groups)
        GetUserRoleResponse roleResponse = grpcExceptionHandler.handleGrpcCall(
                () -> identityServiceClient.getUserRole(userId),
                "getUserRole[addMember]");
        if (roleResponse.getRole() != UserRole.STUDENT) {
            throw new BadRequestException(
                "INVALID_ROLE",
                "Only STUDENT users can be added to groups. User role: " + roleResponse.getRole()
            );
        }
        
        // Check if user already in a group for this semester
        if (membershipRepository.existsByUserIdAndSemesterId(userId, group.getSemesterId())) {
            throw ConflictException.userAlreadyInGroupSameSemester(userId, group.getSemesterId());
        }
        
        // Create membership (default role: MEMBER)
        UserSemesterMembershipId id = new UserSemesterMembershipId(userId, group.getSemesterId());
        UserSemesterMembership membership = UserSemesterMembership.builder()
            .id(id)
            .groupId(groupId)
            .groupRole(GroupRole.MEMBER)
            .build();
        
        membership = membershipRepository.save(membership);
        log.info("UC24 - Member added successfully: groupId={}, userId={}, role=MEMBER", groupId, userId);
        
        return toMemberResponse(membership);
    }
    
    @Override
    public List<MemberResponse> getGroupMembers(Long groupId) {
        log.info("Getting members of group: {}", groupId);
        
        // Validate group exists
        groupRepository.findByIdAndNotDeleted(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        List<UserSemesterMembership> memberships = membershipRepository.findAllByGroupId(groupId);
        
        return memberships.stream()
            .map(this::toMemberResponse)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public MemberResponse promoteToLeader(Long groupId, Long userId) {
        log.info("UC25 - Promoting user {} to LEADER in group {}", userId, groupId);
        
        // Validate group exists
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "GROUP_NOT_FOUND",
                "Group not found: " + groupId
            ));
        
        // Validate membership exists
        UserSemesterMembershipId id = new UserSemesterMembershipId(userId, group.getSemesterId());
        UserSemesterMembership membership = membershipRepository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "USER_NOT_FOUND",
                "User " + userId + " not in group " + groupId
            ));
        
        // Check if already deleted
        if (membership.isDeleted()) {
            throw new ResourceNotFoundException(
                "MEMBERSHIP_DELETED",
                "Membership is deleted"
            );
        }
        
        // If already LEADER, return (idempotent)
        if (membership.getGroupRole() == GroupRole.LEADER) {
            log.info("User {} is already LEADER", userId);
            return toMemberResponse(membership);
        }
        
        // Use pessimistic lock to find and demote current leader (prevent race condition)
        Optional<UserSemesterMembership> currentLeader = 
            membershipRepository.findLeaderByGroupIdWithLock(groupId);
        
        if (currentLeader.isPresent()) {
            log.info("Demoting current leader: userId={}", currentLeader.get().getId().getUserId());
            currentLeader.get().demoteToMember();
            membershipRepository.save(currentLeader.get());
        }
        
        // Promote to LEADER
        membership.promoteToLeader();
        membership = membershipRepository.save(membership);
        
        log.info("UC25 - User {} promoted to LEADER successfully", userId);
        return toMemberResponse(membership);
    }
    
    @Override
    @Transactional
    public MemberResponse demoteToMember(Long groupId, Long userId) {
        log.info("UC25 - Demoting user {} to MEMBER in group {}", userId, groupId);
        
        // Validate group exists
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserSemesterMembershipId id = new UserSemesterMembershipId(userId, group.getSemesterId());
        UserSemesterMembership membership = membershipRepository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> ResourceNotFoundException.memberNotFound(userId, groupId));
        
        // Check if user is actually a LEADER
        if (membership.getGroupRole() != GroupRole.LEADER) {
            throw new BadRequestException(
                "NOT_A_LEADER",
                "User " + userId + " is not a LEADER"
            );
        }
        
        // Demote to MEMBER
        membership.demoteToMember();
        membership = membershipRepository.save(membership);
        
        log.info("UC25 - User {} demoted to MEMBER successfully", userId);
        return toMemberResponse(membership);
    }
    
    @Override
    @Transactional
    public void removeMember(Long groupId, Long userId, Long deletedByUserId) {
        log.info("UC26 - Removing member from group: groupId={}, userId={}, deletedBy={}", 
            groupId, userId, deletedByUserId);
        
        // Validate group exists
        Group group = groupRepository.findByIdAndNotDeleted(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserSemesterMembershipId id = new UserSemesterMembershipId(userId, group.getSemesterId());
        UserSemesterMembership membership = membershipRepository.findByIdAndNotDeleted(id)
            .orElseThrow(() -> ResourceNotFoundException.memberNotFound(userId, groupId));
        
        // Business Rule: Cannot remove LEADER if group has active members
        // API_CONTRACT.md: Only count MEMBER role users (exclude LEADER, exclude soft-deleted)
        if (membership.getGroupRole() == GroupRole.LEADER) {
            long activeMemberCount = membershipRepository.countByGroupIdAndGroupRoleAndDeletedAtIsNull(
                groupId, 
                GroupRole.MEMBER
            );
            if (activeMemberCount > 0) {
                log.warn("UC26 - Cannot remove LEADER from group {} - {} active MEMBER(s) exist. "
                    + "Business rule violation: CANNOT_REMOVE_LEADER", groupId, activeMemberCount);
                throw ConflictException.cannotRemoveLeader();
            }
            log.info("UC26 - Removing LEADER from group {} - no active MEMBER users exist (count=0)", groupId);
        }
        
        // Soft delete the membership with audit trail
        membership.softDelete(deletedByUserId);
        membershipRepository.save(membership);
        
        log.info("Member removed successfully: groupId={}, userId={}, deletedBy={}", 
            groupId, userId, deletedByUserId);
    }
    
    private MemberResponse toMemberResponse(UserSemesterMembership membership) {
        return MemberResponse.builder()
            .userId(membership.getId().getUserId())
            .groupId(membership.getGroupId())
            .semesterId(membership.getId().getSemesterId())
            .groupRole(membership.getGroupRole())
            .joinedAt(membership.getJoinedAt())
            .updatedAt(membership.getUpdatedAt())
            .build();
    }
}
