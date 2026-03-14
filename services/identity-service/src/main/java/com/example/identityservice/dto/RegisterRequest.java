package com.example.identityservice.dto;

import jakarta.validation.constraints.*;

/**
 * Registration request DTO.
 * @see docs/SRS.md - UC-REGISTER Input
 * 
 * Validation Rules:
 * - email: RFC 5322 compliant, max 255 chars, required, unique
 * - password: 8-128 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special (@$!%*?&)
 * - confirmPassword: must match password
 * - fullName: 2-100 chars, letters/spaces/hyphens only (Unicode supported)
 * - role: STUDENT only for self-registration
 */
public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$",
        message = "Password does not meet requirements"
    )
    String password,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    @Pattern(
        regexp = "^[\\p{L}\\s\\-]{2,100}$",
        message = "Name can only contain letters, spaces, and hyphens"
    )
    String fullName,

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^STUDENT$", message = "Invalid role specified")
    String role
) {
    /**
     * Validate that password and confirmPassword match.
     * Called manually in service layer.
     */
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
