package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for the authenticated user's profile.
 *
 * Carries only the fields appropriate for profile editing. Two distinct
 * considerations drive the field set:
 *
 * <ul>
 *   <li><b>name</b> — editable. Validated as required with a sensible maximum
 *       length. No invented strict rules.</li>
 *   <li><b>email</b> — read-only. StudyFlow uses email as the login
 *       identifier ({@code usernameParameter("email")}); changing it would
 *       affect authentication/security and would require a complete
 *       account-security workflow. It is therefore carried here only so a
 *       future profile view can display it, and it is deliberately NOT
 *       validated for update since it is never submitted as an editable
 *       value.</li>
 * </ul>
 *
 * Securely absent by design (defense against privilege escalation and
 * credential leakage):
 *
 * <ul>
 *   <li><b>password</b> — never carried. Password management belongs to the
 *       change-password phase, never a normal profile update.</li>
 *   <li><b>role</b> — never editable by the user. Role stays controlled by
 *       backend/security logic; a user can never submit {@code role=ADMIN}.</li>
 *   <li><b>userId</b> — not used as an ownership mechanism. The authenticated
 *       Spring Security principal identifies the owner.</li>
 * </ul>
 */
public record ProfileDto(

        /**
         * Display name of the user. The only editable profile field.
         */
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        /**
         * Email used for login. Read-only for profile purposes: it is not
         * submitted as an editable value, so it carries no update validation.
         */
        String email

) {

    /**
     * Static factory mapping a {@link User} onto this DTO.
     *
     * The mapping intentionally copies only the approved profile fields
     * (name) plus the read-only email needed for display. No password, role
     * or id is ever carried across.
     *
     * @param user the owning user (already resolved from the security context)
     * @return a copy-safe DTO, or {@code null} when the user is null
     */
    public static ProfileDto from(User user) {
        if (user == null) {
            return null;
        }
        return new ProfileDto(user.getName(), user.getEmail());
    }
}
