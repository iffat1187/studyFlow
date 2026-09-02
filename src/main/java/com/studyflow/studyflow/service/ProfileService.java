package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ChangePasswordDto;
import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.exception.InvalidImageException;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Validate and store a new profile picture for the current user.
     *
     * The image is validated on the server (content must decode as a real
     * JPG/JPEG/PNG), stored under a generated safe name, and only then is the
     * old image removed. The old file is never touched before the new one is
     * safely on disk.
     *
     * @param file the uploaded image
     * @return the updated {@link ProfileDto}
     * @throws com.studyflow.studyflow.exception.UserNotFoundException
     *         when the current user cannot be resolved
     * @throws InvalidImageException
     *         when the uploaded file is rejected (empty, oversize, wrong type,
     *         undecodable content)
     * @throws com.studyflow.studyflow.exception.ProfileImageStoreException
     *         when the file cannot be stored to disk
     */
    ProfileDto uploadProfileImage(MultipartFile file);

    /**
     * Remove the current user's profile picture.
     *
     * Deletes the owned file (when present) and clears the stored reference.
     * The client can never specify which file to delete — only the current
     * user's own stored picture is touched.
     *
     * @return the updated {@link ProfileDto}
     * @throws com.studyflow.studyflow.exception.UserNotFoundException
     *         when the current user cannot be resolved
     */
    ProfileDto removeProfileImage();

    /**
     * The current user's stored profile picture file name, or {@code null}
     * when they have no picture.
     *
     * @return the safe stored file name, or null
     */
    String getCurrentUserProfileImageName();

    /**
     * Change the current authenticated user's password.
     *
     * Verifies the current password against the stored hash, ensures the new
     * password differs from the old, confirms the new passwords match, then
     * encodes and persists the new password. The identity is resolved
     * exclusively from the Spring Security context — no userId is accepted.
     *
     * @param dto the submitted change-password data
     * @throws com.studyflow.studyflow.exception.UserNotFoundException
     *         when the current user cannot be resolved
     * @throws com.studyflow.studyflow.exception.InvalidPasswordOperationException
     *         when the current password is wrong, new passwords do not match,
     *         or the new password is the same as the current one
     */
    void changePassword(ChangePasswordDto dto);
}
