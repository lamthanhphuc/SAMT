package com.example.user_groupservice.service;

import com.example.user_groupservice.dto.request.UpdateUserRequest;
import com.example.user_groupservice.dto.response.PageResponse;
import com.example.user_groupservice.dto.response.UserGroupsResponse;
import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.entity.UserStatus;

import java.util.List;
import java.util.UUID;

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
    UserResponse getUserById(UUID userId, UUID actorId, List<String> actorRoles);
    
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
    UserResponse updateUser(UUID userId, UpdateUserRequest request, 
                           UUID actorId, List<String> actorRoles);
    
    /**
     * List all users with pagination and filters.
     * Authorization: ADMIN only
     * 
     * @param page Page number (0-based)
     * @param size Page size
     * @param status Optional status filter
     * @param role Optional role filter
     * @return Paginated list of users
     */
    PageResponse<UserResponse> listUsers(int page, int size, UserStatus status, String role);
    
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
    UserGroupsResponse getUserGroups(UUID userId, String semester, 
                                     UUID actorId, List<String> actorRoles);
}
