package com.example.project_configservice.exception;

/**
 * Exception thrown when a User-Group Service request times out.
 */
public class GatewayTimeoutException extends RuntimeException {
    
    public GatewayTimeoutException(String message) {
        super(message);
    }
}
