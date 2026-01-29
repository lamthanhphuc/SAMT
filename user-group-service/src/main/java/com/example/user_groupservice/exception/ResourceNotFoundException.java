package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception for resource not found errors (HTTP 404).
 */
public class ResourceNotFoundException extends BaseException {
    
    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
    
    /**
     * User not found.
     */
    public static ResourceNotFoundException userNotFound(UUID userId) {
        return new ResourceNotFoundException(
            "USER_NOT_FOUND",
            String.format("User with ID %s not found", userId)
        );
    }
    
    /**
     * Group not found.
     */
    public static ResourceNotFoundException groupNotFound(UUID groupId) {
        return new ResourceNotFoundException(
            "GROUP_NOT_FOUND",
            String.format("Group with ID %s not found", groupId)
        );
    }
    
    /**
     * Lecturer not found.
     */
    public static ResourceNotFoundException lecturerNotFound(UUID lecturerId) {
        return new ResourceNotFoundException(
            "LECTURER_NOT_FOUND",
            String.format("Lecturer with ID %s not found", lecturerId)
        );
    }
    
    /**
     * Member not found in group.
     */
    public static ResourceNotFoundException memberNotFound(UUID userId, UUID groupId) {
        return new ResourceNotFoundException(
            "USER_NOT_FOUND",
            String.format("User %s is not a member of group %s", userId, groupId)
        );
    }
}
