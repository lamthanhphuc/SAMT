package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when token is invalid (not found or revoked).
 * @see docs/SRS.md - UC-REFRESH-TOKEN Alternate Flow A2
 * Response: 401 Unauthorized - "Token invalid"
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenInvalidException extends RuntimeException {

    public TokenInvalidException() {
        super("Token invalid");
    }

    public TokenInvalidException(String message) {
        super(message);
    }
}
