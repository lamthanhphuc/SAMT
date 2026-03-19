package com.example.analysisservice.web;

/**
 * Signals that the AI model responded, but the content was empty/invalid/malformed
 * relative to the required contract. This is not an upstream infrastructure failure.
 */
public class AiModelOutputException extends RuntimeException {

    public AiModelOutputException(String message) {
        super(message);
    }

    public AiModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}

