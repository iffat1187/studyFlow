package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO for the "Forgot password?" form where the user enters their email.
 * <p>
 * Validation is consistent with the registration email field.
 */
public record ForgotPasswordDto(

        /**
         * Email address associated with the account.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        String email

) {
}
