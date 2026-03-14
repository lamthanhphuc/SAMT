package com.samt.projectconfig.exception;

/**
 * Exception for HTTP 409 Conflict status.
 * 
 * Used for:
 * - Optimistic locking conflicts (concurrent updates detected)
 * - State machine violations
 * - Business rule conflicts
 * 
 * @author Production Team
 */
public class ConflictException extends RuntimeException {
    
    public ConflictException(String message) {
        super(message);
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
