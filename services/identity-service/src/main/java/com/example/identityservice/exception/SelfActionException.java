package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when admin attempts to perform action on own account.
 * @see docs/Security-Review.md - Section 12. Exception Handling Requirements
 * @see docs/Authentication-Authorization-Design.md - Section 8.4 Admin Self-Action Prevention
 * Response: 400 Bad Request - "Cannot perform this action on own account"
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SelfActionException extends RuntimeException {

    public SelfActionException() {
        super("Cannot perform this action on own account");
    }

    public SelfActionException(String message) {
        super(message);
    }
}
