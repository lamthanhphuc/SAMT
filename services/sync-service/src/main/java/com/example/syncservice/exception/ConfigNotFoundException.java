package com.example.syncservice.exception;

import java.util.UUID;

public class ConfigNotFoundException extends RuntimeException {

    public ConfigNotFoundException(UUID configId) {
        super("Config not found: " + configId);
    }

    public ConfigNotFoundException(String message) {
        super(message);
    }
}