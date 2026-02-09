package com.fpt.projectconfig.exception;

import java.util.UUID;

public class ConfigAlreadyExistsException extends RuntimeException {
    private final UUID groupId;

    public ConfigAlreadyExistsException(UUID groupId) {
        super("Group already has a configuration: " + groupId);
        this.groupId = groupId;
    }

    public UUID getGroupId() {
        return groupId;
    }
}
