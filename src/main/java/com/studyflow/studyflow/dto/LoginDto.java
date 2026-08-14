package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object (DTO) for User login.
 *
 * This record is used to transfer login form input
 * between the Controller and the Spring Security layer.
 */
public record LoginDto(

        /**
         * Email address used for login.
         */
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        String email,

        /**
         * Password used for login.
         */
        @NotBlank(message = "Password is required")
        String password

) {
}