package com.example.identityservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when account is locked.
 * @see docs/SRS.md - UC-LOGIN Alternate Flow A3
 * Response: 403 Forbidden - "Account is locked"
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is locked");
    }

    public AccountLockedException(String message) {
        super(message);
    }
}
