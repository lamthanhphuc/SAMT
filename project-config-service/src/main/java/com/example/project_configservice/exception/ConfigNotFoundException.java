package com.example.project_configservice.exception;

import java.util.UUID;

/**
 * Exception thrown when a project configuration is not found.
 */
public class ConfigNotFoundException extends RuntimeException {
    
    public ConfigNotFoundException(UUID configId) {
        super("Configuration not found: " + configId);
    }
    
    public ConfigNotFoundException(String message) {
        super(message);
    }
}
