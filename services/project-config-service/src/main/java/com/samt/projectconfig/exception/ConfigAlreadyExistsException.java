package com.samt.projectconfig.exception;

/**
 * Exception thrown when config already exists for a group.
 * Maps to HTTP 409.
 */
public class ConfigAlreadyExistsException extends RuntimeException {
    
    public ConfigAlreadyExistsException(Long groupId) {
        super("Configuration already exists for group: " + groupId);
    }
    
    public ConfigAlreadyExistsException(String message) {
        super(message);
    }
}
