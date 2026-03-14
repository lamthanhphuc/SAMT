package com.samt.projectconfig.exception;

/**
 * Exception thrown for invalid arguments or malformed requests.
 * Maps to HTTP 400.
 */
public class BadRequestException extends RuntimeException {
    
    public BadRequestException(String message) {
        super(message);
    }
    
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
