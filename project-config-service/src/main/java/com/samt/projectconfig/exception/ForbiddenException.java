package com.samt.projectconfig.exception;

/**
 * Exception thrown when user is not authorized to perform operation.
 * Maps to HTTP 403.
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
}
