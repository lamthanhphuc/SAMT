package com.fpt.projectconfig.exception;

/**
 * Thrown when encryption/decryption fails
 * Maps to 500 INTERNAL_SERVER_ERROR
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
