package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.AddMemberRequest;
import com.example.user_groupservice.dto.request.AssignRoleRequest;
import com.example.user_groupservice.dto.response.GroupMembersResponse;
import com.example.user_groupservice.dto.response.MemberResponse;
import com.example.user_groupservice.entity.GroupRole;

import java.util.UUID;

/**
 * Service interface for group member operations.
 */
public interface GroupMemberService {
    
    /**
     * Add a member to a group.
     * Authorization: ADMIN only
     * 
     * Business rules:
     * - User must exist and be ACTIVE
     * - User can only be in ONE group per semester
     * - If isLeader=true, group must not have existing leader
     * 
     * @param groupId Group ID
     * @param request Add member request
     * @return MemberResponse
     */
    MemberResponse addMember(UUID groupId, AddMemberRequest request);
    
    /**
     * Assign role to a group member.
     * Authorization: ADMIN only
     * 
     * Business rules:
     * - If assigning LEADER, old leader is auto-demoted to MEMBER
     * - Must run in a single transaction
     * 
     * @param groupId Group ID
     * @param userId User ID
     * @param request Assign role request
     * @return Updated MemberResponse
     */
    MemberResponse assignRole(UUID groupId, Long userId, AssignRoleRequest request);
    
    /**
     * Remove a member from a group (soft delete).
     * Authorization: ADMIN only
     * 
     * Business rules:
     * - Cannot remove LEADER if group has other members
     * 
     * @param groupId Group ID
     * @param userId User ID
     */
    void removeMember(UUID groupId, Long userId);
    
    /**
     * Get all members of a group.
     * Authorization: AUTHENTICATED
     * 
     * @param groupId Group ID
     * @param role Optional role filter
     * @return GroupMembersResponse
     */
    GroupMembersResponse getGroupMembers(UUID groupId, GroupRole role);
}
