package com.samt.projectconfig.exception;

/**
 * Exception thrown when encryption/decryption fails.
 * Maps to HTTP 500.
 */
public class EncryptionException extends RuntimeException {
    
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EncryptionException(String message) {
        super(message);
    }
}
