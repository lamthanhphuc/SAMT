package com.fpt.projectconfig.exception;

/**
 * Thrown when user doesn't have permission to perform operation
 * Maps to 403 FORBIDDEN
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
