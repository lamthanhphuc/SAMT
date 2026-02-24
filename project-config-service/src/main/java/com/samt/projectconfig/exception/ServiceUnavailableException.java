package com.samt.projectconfig.exception;

/**
 * Exception thrown when dependency service (User-Group, Identity) is unavailable.
 * Maps to HTTP 503.
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String service) {
        super(service + " is unavailable, please try again later");
    }
    
    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable", cause);
    }
}
