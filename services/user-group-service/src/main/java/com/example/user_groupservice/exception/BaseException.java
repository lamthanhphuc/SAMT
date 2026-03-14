package com.example.user_groupservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception class for all business exceptions.
 */
@Getter
public abstract class BaseException extends RuntimeException {
    
    private final String code;
    private final HttpStatus status;
    
    protected BaseException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
