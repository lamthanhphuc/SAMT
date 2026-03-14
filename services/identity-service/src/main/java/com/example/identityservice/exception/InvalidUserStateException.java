package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when user is in an invalid state for the requested operation.
 * @see docs/Security-Review.md - Section 12. Exception Handling Requirements
 * Response: 400 Bad Request - Dynamic message
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidUserStateException extends RuntimeException {

    public InvalidUserStateException(String message) {
        super(message);
    }
}
