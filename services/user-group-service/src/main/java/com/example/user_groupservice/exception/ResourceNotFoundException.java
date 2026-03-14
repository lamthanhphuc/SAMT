package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

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
    public static ResourceNotFoundException userNotFound(Long userId) {
        return new ResourceNotFoundException(
            "USER_NOT_FOUND",
            String.format("User with ID %d not found", userId)
        );
    }
    
    /**
     * Group not found.
     */
    public static ResourceNotFoundException groupNotFound(Long groupId) {
        return new ResourceNotFoundException(
            "GROUP_NOT_FOUND",
            String.format("Group with ID %d not found", groupId)
        );
    }
    
    /**
     * Lecturer not found.
     */
    public static ResourceNotFoundException lecturerNotFound(Long lecturerId) {
        return new ResourceNotFoundException(
            "LECTURER_NOT_FOUND",
            String.format("Lecturer with ID %d not found", lecturerId)
        );
    }
    
    /**
     * Member not found in group.
     */
    public static ResourceNotFoundException memberNotFound(Long userId, Long groupId) {
        return new ResourceNotFoundException(
            "USER_NOT_FOUND",
            String.format("User %d is not a member of group %d", userId, groupId)
        );
    }
    
    /**
     * Semester not found.
     */
    public static ResourceNotFoundException semesterNotFound(Long semesterId) {
        return new ResourceNotFoundException(
            "SEMESTER_NOT_FOUND",
            String.format("Semester with ID %d not found", semesterId)
        );
    }
}
