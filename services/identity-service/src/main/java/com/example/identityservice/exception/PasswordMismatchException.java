package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when passwords don't match during registration.
 * @see docs/SRS.md - UC-REGISTER Alternate Flow A4
 * Response: 400 Bad Request - "Passwords do not match"
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException() {
        super("Passwords do not match");
    }
}
