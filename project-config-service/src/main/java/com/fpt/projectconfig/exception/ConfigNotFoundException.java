package com.fpt.projectconfig.exception;

import java.util.UUID;

/**
 * Thrown when config is not found or soft-deleted
 * Maps to 404 CONFIG_NOT_FOUND
 */
public class ConfigNotFoundException extends RuntimeException {

    private final UUID configId;

    public ConfigNotFoundException(UUID configId) {
        super("Configuration not found: " + configId);
        this.configId = configId;
    }

    public UUID getConfigId() {
        return configId;
    }
}
