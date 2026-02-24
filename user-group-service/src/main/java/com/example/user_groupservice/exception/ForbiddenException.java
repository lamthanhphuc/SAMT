package com.example.user_groupservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for forbidden access (HTTP 403).
 * User is authenticated but lacks permission.
 */
public class ForbiddenException extends BaseException {
    
    public ForbiddenException(String code, String message) {
        super(code, message, HttpStatus.FORBIDDEN);
    }
    
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
    
    /**
     * Insufficient permission to perform action.
     */
    public static ForbiddenException insufficientPermission() {
        return new ForbiddenException("You do not have permission to perform this action");
    }
    
    /**
     * Cannot access other user's resources.
     */
    public static ForbiddenException cannotAccessOtherUser() {
        return new ForbiddenException("You can only access your own profile");
    }
    
    /**
     * Lecturer cannot update profile via this API.
     * Per spec: LECTURER role is explicitly excluded from UC22.
     */
    public static ForbiddenException lecturerCannotUpdateProfile() {
        return new ForbiddenException("Lecturers cannot update profiles via this API");
    }
    
    /**
     * Lecturer can only view students.
     * Per spec (UC21): LECTURER can only view users with STUDENT role.
     */
    public static ForbiddenException lecturerCanOnlyViewStudents() {
        return new ForbiddenException("LECTURER_CANNOT_VIEW_NON_STUDENT", 
            "Lecturers can only view student profiles");
    }
    
    /**
     * Lecturer can only view students they supervise.
     * Privacy enhancement: LECTURER can only access students in groups they teach.
     */
    public static ForbiddenException lecturerCanOnlyViewSupervisedStudents() {
        return new ForbiddenException("LECTURER_CANNOT_VIEW_UNSUPERVISED_STUDENT",
            "Lecturer can only view students in groups they supervise");
    }
    
    /**
     * Role not allowed for this operation.
     */
    public static ForbiddenException roleNotAllowed(String requiredRole) {
        return new ForbiddenException(
            String.format("This action requires %s role", requiredRole)
        );
    }
}
