package com.example.identityservice.exception;

/**
 * Indicates an upstream dependency returned an error or timed out.
 * Mapped to HTTP 502 by GlobalExceptionHandler.
 */
public class BadGatewayException extends RuntimeException {
    public BadGatewayException(String message) {
        super(message);
    }

    public BadGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

