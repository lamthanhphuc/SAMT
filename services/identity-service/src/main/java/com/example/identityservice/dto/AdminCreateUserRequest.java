package com.example.identityservice.dto;

import jakarta.validation.constraints.*;

/**
 * Admin request DTO for creating user accounts with any role.
 * Only accessible by ADMIN role.
 * 
 * @see docs/API_CONTRACT.md - Admin endpoints
 */
public record AdminCreateUserRequest(
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

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    @Pattern(
        regexp = "^[\\p{L}\\s\\-]{2,100}$",
        message = "Name can only contain letters, spaces, and hyphens"
    )
    String fullName,

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(STUDENT|LECTURER|ADMIN)$", message = "Role must be STUDENT, LECTURER, or ADMIN")
    String role
) {
}
