package com.fpt.projectconfig.exception;

import java.util.UUID;

/**
 * Thrown when group is not found or soft-deleted
 * Maps to 404 GROUP_NOT_FOUND
 */
public class GroupNotFoundException extends RuntimeException {

    private final UUID groupId;

    public GroupNotFoundException(UUID groupId) {
        super("Group not found or deleted: " + groupId);
        this.groupId = groupId;
    }

    public UUID getGroupId() {
        return groupId;
    }
}
