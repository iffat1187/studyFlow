package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for the authenticated user's change-password flow.
 *
 * Carries only the three values needed to perform a password change.
 * Validation is consistent with the existing registration password
 * requirements ({@code min = 6}) to avoid an inconsistent security policy.
 *
 * Securely absent by design:
 *
 * <ul>
 *   <li><b>userId</b> — never accepted. The owner is resolved from the
 *       Spring Security context.</li>
 *   <li><b>email</b> — never included.</li>
 *   <li><b>passwordHash</b> — never exposed or returned.</li>
 * </ul>
 */
public record ChangePasswordDto(

        /**
         * The user's current password, used to verify identity before
         * allowing the change.
         */
        @NotBlank(message = "Current password is required")
        String currentPassword,

        /**
         * The desired new password. Must be at least 6 characters, matching
         * the registration policy.
         */
        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "New password must be at least 6 characters")
        String newPassword,

        /**
         * Confirmation of the new password. Must match {@code newPassword};
         * this check is performed in the service layer, not via annotation.
         */
        @NotBlank(message = "Please confirm your new password")
        String confirmPassword

) {
}
