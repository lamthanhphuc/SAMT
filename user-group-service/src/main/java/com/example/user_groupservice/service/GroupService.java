package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateLecturerRequest;
import com.example.user_groupservice.dto.response.GroupDetailResponse;
import com.example.user_groupservice.dto.response.GroupListResponse;
import com.example.user_groupservice.dto.response.GroupResponse;
import com.example.user_groupservice.dto.response.PageResponse;

/**
 * Service interface for group operations.
 */
public interface GroupService {
    
    /**
     * Create a new group.
     * Authorization: ADMIN only
     * 
     * @param request Create request
     * @return GroupResponse with created group
     */
    GroupResponse createGroup(CreateGroupRequest request);
    
    /**
     * Get group details by ID.
     * Authorization: AUTHENTICATED
     * 
     * @param groupId Group ID
     * @return GroupDetailResponse with members
     */
    GroupDetailResponse getGroupById(Long groupId);
    
    /**
     * List groups with pagination and filters.
     * Authorization: AUTHENTICATED
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param semesterId Optional semester ID filter
     * @param lecturerId Optional lecturer filter
     * @return Paginated list of groups
     */
    PageResponse<GroupListResponse> listGroups(int page, int size, 
                                               Long semesterId, Long lecturerId);
    
    /**
     * Update a group.
     * Authorization: ADMIN only
     * Note: Semester is immutable
     * 
     * @param groupId Group ID
     * @param request Update request
     * @return Updated GroupResponse
     */
    GroupResponse updateGroup(Long groupId, UpdateGroupRequest request);
    
    /**
     * Delete a group (soft delete).
     * Authorization: ADMIN only
     * 
     * @param groupId Group ID
     * @param deletedByUserId User ID who performs the deletion
     */
    void deleteGroup(Long groupId, Long deletedByUserId);
    
    /**
     * UC27 - Update Group Lecturer.
     * Authorization: ADMIN only
     * 
     * @param groupId Group ID
     * @param request Update lecturer request
     * @return Updated GroupResponse
     */
    GroupResponse updateGroupLecturer(Long groupId, UpdateLecturerRequest request);
}
