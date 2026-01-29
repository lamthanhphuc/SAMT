package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.CreateGroupRequest;
import com.example.user_groupservice.dto.request.UpdateGroupRequest;
import com.example.user_groupservice.dto.response.GroupDetailResponse;
import com.example.user_groupservice.dto.response.GroupListResponse;
import com.example.user_groupservice.dto.response.GroupResponse;
import com.example.user_groupservice.dto.response.PageResponse;

import java.util.UUID;

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
    GroupDetailResponse getGroupById(UUID groupId);
    
    /**
     * List groups with pagination and filters.
     * Authorization: AUTHENTICATED
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param semester Optional semester filter
     * @param lecturerId Optional lecturer filter
     * @return Paginated list of groups
     */
    PageResponse<GroupListResponse> listGroups(int page, int size, 
                                               String semester, UUID lecturerId);
    
    /**
     * Update a group.
     * Authorization: ADMIN only
     * Note: Semester is immutable
     * 
     * @param groupId Group ID
     * @param request Update request
     * @return Updated GroupResponse
     */
    GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request);
    
    /**
     * Delete a group (soft delete).
     * Authorization: ADMIN only
     * 
     * @param groupId Group ID
     */
    void deleteGroup(UUID groupId);
}
