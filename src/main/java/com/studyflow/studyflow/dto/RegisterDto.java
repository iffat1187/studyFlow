package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for User registration.
 *
 * This record is used to transfer registration form input
 * between the Controller and Service layers without exposing
 * the User entity directly.
 *
 * Password comparison is intentionally NOT performed here;
 * it will be handled by registration logic in a later phase.
 */
public record RegisterDto(

        /**
         * Display name of the user.
         */
        @NotBlank(message = "Name is required")
        String name,

        /**
         * Email address used for login.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        String email,

        /**
         * Desired password of the user.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        /**
         * Confirmation of the password (not yet compared here).
         */
        @NotBlank(message = "Please confirm your password")
        String confirmPassword

) {
}