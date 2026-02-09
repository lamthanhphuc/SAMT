package com.fpt.projectconfig.exception;

public class VerificationException extends RuntimeException {
    private final String service;

    public VerificationException(String service, String message) {
        super(message);
        this.service = service;
    }

    public VerificationException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
