package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for invalid business logic requests.
 * Returns HTTP 400 BAD_REQUEST.
 */
public class BadRequestException extends BaseException {
    
    public BadRequestException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
    
    public static BadRequestException invalidRole(String role, String expected) {
        return new BadRequestException("INVALID_ROLE", 
            String.format("Invalid role: %s. Expected: %s", role, expected));
    }
}
