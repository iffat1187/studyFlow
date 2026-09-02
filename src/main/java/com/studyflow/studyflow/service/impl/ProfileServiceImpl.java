package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.ChangePasswordDto;
import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidImageException;
import com.studyflow.studyflow.exception.InvalidPasswordOperationException;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
import com.studyflow.studyflow.exception.ProfileImageStoreException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ProfileService;
import com.studyflow.studyflow.storage.ProfileImageStorage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Implementation of {@link ProfileService} operating on the current
 * authenticated user.
 *
 * Security model:
 *
 * <pre>
 * SecurityContext (authenticated principal / email)
 *         |
 *         v
 *   UserRepository.findByEmail(principal)
 *         |
 *         v
 *      Current User
 *         |
 *         v
 *      ProfileDto
 * </pre>
 *
 * The identity is derived exclusively from the Spring Security context — the
 * caller never supplies a userId, hidden field, URL parameter or email. This
 * is the source of truth for ownership, so one user can never read or modify
 * another user's profile (IDOR prevention), and the service can never become
 * an administrative user-update mechanism.
 *
 * Only the approved editable field (display name) is ever written to the
 * {@code User}. Email, password/hash and role are never modified, preserving
 * login identity and privileges. If the principal cannot be resolved to a
 * real user, a {@link UserNotFoundException} is thrown rather than returning
 * {@code null} (which would leak into a NPE later).
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileServiceImpl.class);

    private final UserRepository userRepository;
    private final Validator validator;
    private final ProfileImageStorage imageStorage;
    private final PasswordEncoder passwordEncoder;

    /**
     * Constructor injection.
     *
     * @param userRepository  resolves the current user by the authenticated email
     * @param validator       performs Bean Validation on submitted profile data
     * @param imageStorage    low-level storage/validation of profile pictures
     * @param passwordEncoder encodes and verifies passwords
     */
    public ProfileServiceImpl(UserRepository userRepository, Validator validator,
                              ProfileImageStorage imageStorage,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.validator = validator;
        this.imageStorage = imageStorage;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getCurrentUserProfile() {
        return ProfileDto.from(resolveCurrentUser());
    }

    @Override
    @Transactional
    public ProfileDto updateCurrentUserProfile(ProfileDto profileDto) {
        User currentUser = resolveCurrentUser();

        // Defensive validation: rejects null/blank/over-long name without
        // relying on a controller to bind the DTO first. Never persist
        // invalid profile data.
        Set<ConstraintViolation<ProfileDto>> violations = validator.validate(profileDto);
        if (!violations.isEmpty()) {
            ConstraintViolation<ProfileDto> first = violations.iterator().next();
            throw new InvalidProfileDataException(first.getMessage());
        }

        // Copy ONLY the approved editable field. Email/password/role/id are
        // never written, so authentication identity and privileges survive.
        currentUser.setName(profileDto.name());

        return ProfileDto.from(userRepository.save(currentUser));
    }

    @Override
    @Transactional
    public ProfileDto uploadProfileImage(MultipartFile file) {
        User currentUser = resolveCurrentUser();

        // Validate content + store under a generated safe name. On any
        // rejection nothing is persisted and the old image stays intact.
        String newName = imageStorage.store(file);

        String oldName = currentUser.getProfileImagePath();
        currentUser.setProfileImagePath(newName);

        User saved;
        try {
            saved = userRepository.save(currentUser);
        } catch (RuntimeException e) {
            // The DB rejected the update (e.g. optimistic/constraint failure).
            // Best-effort cleanup of the just-written file so we do not leak
            // an orphan image; the user's old picture was never invalidated.
            imageStorage.delete(newName);
            throw e;
        }

        // Remove the old image only AFTER the new reference is safely saved.
        if (oldName != null && !oldName.equals(newName)) {
            if (!imageStorage.delete(oldName)) {
                // Non-fatal: the user's new picture is active and correct; the
                // orphaned old file is harmless and will not break anything.
                log.warn("Could not delete previous profile image {} for user {}",
                        oldName, currentUser.getEmail());
            }
        }

        return ProfileDto.from(saved);
    }

    @Override
    @Transactional
    public ProfileDto removeProfileImage() {
        User currentUser = resolveCurrentUser();
        String oldName = currentUser.getProfileImagePath();
        if (oldName == null) {
            return ProfileDto.from(currentUser);
        }

        currentUser.setProfileImagePath(null);
        User saved = userRepository.save(currentUser);

        // Deleting the file is done after the reference is cleared. A failure
        // here is logged and non-fatal: the picture simply becomes an orphan
        // rather than leaving a dangling reference or breaking the user.
        if (!imageStorage.delete(oldName)) {
            log.warn("Could not delete removed profile image {} for user {}",
                    oldName, currentUser.getEmail());
        }

        return ProfileDto.from(saved);
    }

    @Override
    public String getCurrentUserProfileImageName() {
        return resolveCurrentUser().getProfileImagePath();
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDto dto) {
        User currentUser = resolveCurrentUser();

        // Defensive validation: rejects null/blank/short passwords without
        // relying on the controller to bind the DTO first.
        Set<ConstraintViolation<ChangePasswordDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            ConstraintViolation<ChangePasswordDto> first = violations.iterator().next();
            throw new InvalidPasswordOperationException(first.getMessage());
        }

        // Verify the current password against the stored hash.
        if (!passwordEncoder.matches(dto.currentPassword(), currentUser.getPassword())) {
            throw new InvalidPasswordOperationException("Current password is incorrect.");
        }

        // Reject if the new password is the same as the current one.
        if (passwordEncoder.matches(dto.newPassword(), currentUser.getPassword())) {
            throw new InvalidPasswordOperationException(
                    "New password must be different from your current password.");
        }

        // Confirm new passwords match.
        if (!dto.newPassword().equals(dto.confirmPassword())) {
            throw new InvalidPasswordOperationException("New passwords do not match.");
        }

        // Encode and save the new password. Never store raw passwords.
        currentUser.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(currentUser);
    }

    /**
     * Resolve the current user from the Spring Security context.
     *
     * The principal is a {@code UserDetails} whose username is the login
     * email (StudyFlow uses email as the login identifier). The same value
     * is mapped to every user's {@code name} during registration via
     * {@code authentication.getName()} in the web layer.
     *
     * @return the current authenticated {@link User}
     * @throws UserNotFoundException when there is no principal or no matching
     *                               {@code User} record
     */
    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UserNotFoundException("No authenticated user.");
        }
        Object principal = authentication.getPrincipal();
        String email = (principal instanceof org.springframework.security.core.userdetails.UserDetails details)
                ? details.getUsername()
                : authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Current user could not be resolved."));
    }
}
