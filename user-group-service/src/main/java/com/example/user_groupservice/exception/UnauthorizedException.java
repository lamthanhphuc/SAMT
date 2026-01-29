package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for unauthorized access (HTTP 401).
 */
public class UnauthorizedException extends BaseException {
    
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * Invalid or missing token.
     */
    public static UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or missing authentication token");
    }
    
    /**
     * Token expired.
     */
    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException("Authentication token has expired");
    }
}
