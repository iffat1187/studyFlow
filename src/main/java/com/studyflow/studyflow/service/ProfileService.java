package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ProfileDto;

/**
 * Secure service for the CURRENT authenticated user's profile.
 *
 * Every operation resolves the owner from the Spring Security
 * {@code SecurityContext} — never from a userId, hidden field, URL parameter
 * or submitted email. This makes the service inherently IDOR-proof: a caller
 * has no way to target another user's profile.
 *
 * The service is deliberately <b>not</b> an administrative user-update
 * mechanism. It may only read and update the profile of the principal that
 * is currently authenticated, and it only ever touches the approved editable
 * field(s) defined in the profile phase (display name). Admin user management
 * lives in the separate admin architecture.
 */
public interface ProfileService {

    /**
     * The current authenticated user's profile.
     *
     * @return the {@link ProfileDto} for the current user
     * @throws com.studyflow.studyflow.exception.UserNotFoundException
     *         when the authenticated principal cannot be resolved to an
     *         existing user (never returns {@code null})
     */
    ProfileDto getCurrentUserProfile();

    /**
     * Update the current authenticated user's approved editable profile
     * fields and return the resulting profile.
     *
     * Only the display name is modified. Email, password/hash, role and id
     * are never written, regardless of what the submitted DTO contains, so
     * the user's login identity and privileges are preserved.
     *
     * @param profileDto the submitted profile data
     * @return the updated {@link ProfileDto}
     * @throws com.studyflow.studyflow.exception.UserNotFoundException
     *         when the current user cannot be resolved
     * @throws com.studyflow.studyflow.exception.InvalidProfileDataException
     *         when the submitted name is invalid (blank/too long)
     */
    ProfileDto updateCurrentUserProfile(ProfileDto profileDto);
}
