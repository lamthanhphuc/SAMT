package com.example.project_configservice.exception;

/**
 * Exception thrown when a User-Group Service is unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
