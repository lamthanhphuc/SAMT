package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when email already exists during registration.
 * @see docs/SRS.md - UC-REGISTER Alternate Flow A1
 * Response: 409 Conflict - "Email already registered"
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email already registered");
    }

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
