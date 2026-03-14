package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when token is expired.
 * @see docs/SRS.md - UC-REFRESH-TOKEN Alternate Flow A1
 * Response: 401 Unauthorized - "Token expired"
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException() {
        super("Token expired");
    }

    public TokenExpiredException(String message) {
        super(message);
    }
}
