package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

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
    public static ConflictException userAlreadyInGroup(Long userId, Long groupId) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP",
            String.format("User %d is already in group %d", userId, groupId)
        );
    }
    
    /**
     * User is already in a group for the specified semester.
     */
    public static ConflictException userAlreadyInGroupSameSemester(Long userId, Long semesterId) {
        return new ConflictException(
            "USER_ALREADY_IN_GROUP_SAME_SEMESTER",
            String.format("User %d is already in a group for semester %d", userId, semesterId)
        );
    }
    
    /**
     * User is inactive.
     */
    public static ConflictException userInactive(Long userId) {
        return new ConflictException(
            "USER_INACTIVE",
            String.format("User %d is inactive", userId)
        );
    }
    
    /**
     * Group already has a leader.
     */
    public static ConflictException leaderAlreadyExists(Long groupId) {
        return new ConflictException(
            "LEADER_ALREADY_EXISTS",
            String.format("Group %d already has a leader", groupId)
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
    public static ConflictException groupNameDuplicate(String groupName, String semesterCode) {
        return new ConflictException(
            "GROUP_NAME_DUPLICATE",
            String.format("Group name %s already exists in semester %s", groupName, semesterCode)
        );
    }
}
