package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;

import java.util.List;

/**
 * Service interface for user operations.
 */
public interface UserService {
    
    /**
     * Get user profile by ID.
     * Authorization: ADMIN (all users), LECTURER (students), STUDENT (self only)
     * 
     * @param userId User ID to retrieve
     * @param actorId ID of the user performing the action
     * @param actorRoles Roles of the user performing the action
     * @return UserResponse
     */
    UserResponse getUserById(Long userId, Long actorId, List<String> actorRoles);
    
    /**
     * Update user profile.
     * Authorization: ADMIN (any user), STUDENT (self only)
     * 
     * @param userId User ID to update
     * @param request Update request
     * @param actorId ID of the user performing the action
     * @param actorRoles Roles of the user performing the action
     * @return Updated UserResponse
     */
    UserResponse updateUser(Long userId, UpdateUserRequest request, 
                           Long actorId, List<String> actorRoles);
    
    /**
     * List all users with pagination and filters.
     * Authorization: ADMIN only
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param status Optional status filter (ACTIVE/INACTIVE)
     * @param role Optional role filter (ADMIN/LECTURER/STUDENT)
     * @return Paginated list of users
     */
    PageResponse<UserResponse> listUsers(int page, int size, String status, String role);
    
    /**
     * Get all groups that a user belongs to.
     * Authorization: ADMIN (any user), LECTURER (students), STUDENT (self only)
     * 
     * @param userId User ID
     * @param semester Optional semester filter
     * @param actorId ID of the user performing the action
     * @param actorRoles Roles of the user performing the action
     * @return UserGroupsResponse
     */
    UserGroupsResponse getUserGroups(Long userId, String semester, 
                                     Long actorId, List<String> actorRoles);
}
