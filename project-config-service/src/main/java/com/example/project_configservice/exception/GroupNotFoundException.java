package com.example.project_configservice.exception;

import java.util.UUID;

/**
 * Exception thrown when a group is not found in User-Group Service.
 */
public class GroupNotFoundException extends RuntimeException {
    
    public GroupNotFoundException(UUID groupId) {
        super("Group not found or deleted: " + groupId);
    }
    
    public GroupNotFoundException(String message) {
        super(message);
    }
}
