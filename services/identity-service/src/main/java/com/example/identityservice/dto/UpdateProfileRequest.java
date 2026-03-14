package com.example.identityservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating the current user's profile.
 *
 * Safe fields only:
 * - email
 * - fullName
 */
public record UpdateProfileRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    @Pattern(
        regexp = "^[\\p{L}\\s\\-]{2,100}$",
        message = "Name can only contain letters, spaces, and hyphens"
    )
    String fullName
) {
}