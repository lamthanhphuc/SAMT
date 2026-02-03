package com.example.project_configservice.exception;

/**
 * Exception thrown for invalid request parameters.
 */
public class BadRequestException extends RuntimeException {
    
    public BadRequestException(String message) {
        super(message);
    }
}
