package com.studyflow.studyflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for the password-reset form reached via the emailed link.
 * <p>
 * Validation reuses the same password policy as registration and
 * change-password ({@code min = 6}).
 * <p>
 * Securely absent by design:
 * <ul>
 *   <li><b>userId / email</b> — never accepted. The target user is resolved
 *       from the validated token.</li>
 *   <li><b>token</b> — carried only as a hidden form field, never validated
 *       via Bean Validation (it is validated in the service layer).</li>
 * </ul>
 */
public record ResetPasswordDto(

        /**
         * Opaque reset token carried from the email link.
         * Not user-editable; validated server-side.
         */
        @NotBlank(message = "Reset token is missing")
        String token,

        /**
         * Desired new password. Same policy as registration ({@code min = 6}).
         */
        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "New password must be at least 6 characters")
        String newPassword,

        /**
         * Confirmation of the new password. Must match {@code newPassword};
         * this check is performed in the service layer.
         */
        @NotBlank(message = "Please confirm your new password")
        String confirmPassword

) {
}
