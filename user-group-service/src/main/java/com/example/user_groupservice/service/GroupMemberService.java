package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.response.MemberResponse;
import java.util.List;

/**
 * Service interface for group member operations.
 * 
 * Business Rules:
 * - User can only be in ONE group per semester (enforced by PK: user_id, semester_id)
 * - Each group can have only ONE LEADER (enforced by DB unique index)
 * - Only STUDENT role can be added as member
 * - Lecturer must have LECTURER role (validated via gRPC)
 */
public interface GroupMemberService {
    
    /**
     * Add a member to a group
     * @param groupId Group ID
     * @param userId User ID to add
     * @return MemberResponse
     */
    MemberResponse addMember(Long groupId, Long userId);
    
    /**
     * Get all members of a group
     * @param groupId Group ID
     * @return List of MemberResponse
     */
    List<MemberResponse> getGroupMembers(Long groupId);
    
    /**
     * Promote member to LEADER
     * @param groupId Group ID
     * @param userId User ID to promote
     * @return Updated MemberResponse
     */
    MemberResponse promoteToLeader(Long groupId, Long userId);
    
    /**
     * Demote LEADER to MEMBER
     * @param groupId Group ID
     * @param userId User ID to demote
     * @return Updated MemberResponse
     */
    MemberResponse demoteToMember(Long groupId, Long userId);
    
    /**
     * Remove a member from group (soft delete)
     * @param groupId Group ID
     * @param userId User ID to remove
     * @param deletedByUserId User ID who performs the deletion
     */
    void removeMember(Long groupId, Long userId, Long deletedByUserId);
}
