package com.samt.projectconfig.exception;

import java.util.UUID;

/**
 * Exception thrown when configuration is not found.
 * Maps to HTTP 404.
 */
public class ConfigNotFoundException extends RuntimeException {
    
    public ConfigNotFoundException(UUID id) {
        super("Configuration not found: " + id);
    }
    
    public ConfigNotFoundException(String message) {
        super(message);
    }
}
