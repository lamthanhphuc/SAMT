package com.example.project_configservice.exception;

/**
 * Exception thrown when a configuration already exists for a group.
 */
public class ConfigAlreadyExistsException extends RuntimeException {
    
    public ConfigAlreadyExistsException(String message) {
        super(message);
    }
}
