package com.example.project_configservice.exception;

/**
 * Exception thrown when user lacks permission for an operation.
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
}
