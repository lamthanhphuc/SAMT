package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for gRPC UNAVAILABLE errors.
 * Returns HTTP 503 SERVICE_UNAVAILABLE.
 */
public class ServiceUnavailableException extends BaseException {
    
    public ServiceUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    public static ServiceUnavailableException identityServiceUnavailable() {
        return new ServiceUnavailableException("Identity Service is temporarily unavailable");
    }
}
