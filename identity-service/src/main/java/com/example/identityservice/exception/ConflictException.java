package com.example.identityservice.exception;

/**
 * Thrown when attempting to create a duplicate resource (email, external account ID, etc.)
 * 
 * @see docs/API_CONTRACT.md - 409 CONFLICT error code
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
