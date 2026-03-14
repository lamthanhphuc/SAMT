package com.samt.projectconfig.exception;

/**
 * Exception thrown when group is not found in User-Group Service.
 * Maps to HTTP 404.
 */
public class GroupNotFoundException extends RuntimeException {
    
    public GroupNotFoundException(Long groupId) {
        super("Group not found or deleted: " + groupId);
    }
    
    public GroupNotFoundException(String message) {
        super(message);
    }
}
