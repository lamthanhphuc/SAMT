package com.samt.projectconfig.exception;

/**
 * Exception thrown when external API verification fails.
 * Maps to HTTP 504 (Gateway Timeout) or included in verification response.
 */
public class VerificationException extends RuntimeException {
    
    public VerificationException(String message) {
        super(message);
    }
    
    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
