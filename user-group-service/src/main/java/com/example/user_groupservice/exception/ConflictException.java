package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception for conflict errors (HTTP 409).
 * Used for business rule violations.
 */
public class ConflictException extends BaseException {
    
    public ConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
    
    /**
     * User is already in the specified group.
     */
    public static ConflictException userAlreadyInGroup(UUID userId, UUID groupId) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP",
            String.format("User %s is already in group %s", userId, groupId)
        );
    }
    
    /**
     * User is already in a group for the specified semester.
     */
    public static ConflictException userAlreadyInGroupSameSemester(UUID userId, String semester) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP_SAME_SEMESTER",
            String.format("User %s is already in a group for semester %s", userId, semester)
        );
    }
    
    /**
     * User is inactive.
     */
    public static ConflictException userInactive(UUID userId) {
        return new ConflictException(
            "USER_INACTIVE",
            String.format("User %s is inactive", userId)
        );
    }
    
    /**
     * Group already has a leader.
     */
    public static ConflictException leaderAlreadyExists(UUID groupId) {
        return new ConflictException(
            "LEADER_ALREADY_EXISTS",
            String.format("Group %s already has a leader", groupId)
        );
    }
    
    /**
     * Cannot remove leader while group has active members.
     */
    public static ConflictException cannotRemoveLeader() {
        return new ConflictException(
            "CANNOT_REMOVE_LEADER",
            "Cannot remove leader while group has active members"
        );
    }
    
    /**
     * Group name already exists in semester.
     */
    public static ConflictException groupNameDuplicate(String groupName, String semester) {
        return new ConflictException(
            "GROUP_NAME_DUPLICATE",
            String.format("Group name %s already exists in semester %s", groupName, semester)
        );
    }
}
