package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.entity.UserSettings;
import com.studyflow.studyflow.exception.InvalidSettingsDataException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.repository.UserSettingsRepository;
import com.studyflow.studyflow.service.UserSettingsService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Implementation of {@link UserSettingsService} operating on the current
 * authenticated user.
 *
 * Security model (same shape as ProfileServiceImpl):
 *
 * <pre>
 * SecurityContext (authenticated principal / email)
 *         |
 *         v
 *   UserRepository.findByEmail(principal)
 *         |
 *         v
 *       Current User
 *         |
 *         v
 *    UserSettings (row or documented defaults)
 * </pre>
 *
 * The identity is derived exclusively from the Spring Security context — the
 * caller never supplies a userId, hidden field, URL parameter or email. This
 * is the source of truth for ownership, so one user can never read or modify
 * another user's settings (IDOR prevention), and the service can never
 * become an administrative user-update mechanism.
 *
 * Only the approved preference fields are ever written to the settings row.
 * Email, password/hash, role and notification preferences are never touched,
 * preserving login identity, privileges and the separate notification
 * module.
 */
@Service
public class UserSettingsServiceImpl implements UserSettingsService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final Validator validator;

    /**
     * Constructor injection.
     *
     * @param userRepository          resolves the current user by the
     *                                authenticated email
     * @param userSettingsRepository  the per-user settings table (unique user_id)
     * @param validator               performs Bean Validation on submitted settings
     */
    public UserSettingsServiceImpl(UserRepository userRepository,
                                   UserSettingsRepository userSettingsRepository,
                                   Validator validator) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.validator = validator;
    }

    @Override
    @Transactional
    public UserSettingsDto getCurrentUserSettings() {
        User user = resolveCurrentUser();
        // Lazy creation: opening Settings for the first time materializes a
        // row with the documented defaults, so later updates load the row
        // instead of replacing defaults in memory.
        UserSettings settings = userSettingsRepository.findByUser(user)
                .orElseGet(() -> userSettingsRepository.save(newDefaults(user)));
        return UserSettingsDto.from(settings);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettingsDto getSettingsForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with email: " + userEmail));
        return userSettingsRepository.findByUser(user)
                .map(UserSettingsDto::from)
                .orElseGet(UserSettingsDto::defaults);
    }

    @Override
    @Transactional
    public UserSettingsDto updateCurrentUserSettings(UserSettingsDto settingsDto) {
        User currentUser = resolveCurrentUser();

        // Defensive validation: rejects null/invalid durations, session types
        // and goals without relying on a controller to bind the DTO first.
        // Never persist invalid settings data.
        Set<ConstraintViolation<UserSettingsDto>> violations = validator.validate(settingsDto);
        if (!violations.isEmpty()) {
            ConstraintViolation<UserSettingsDto> first = violations.iterator().next();
            throw new InvalidSettingsDataException(first.getMessage());
        }

        // Load the user's own row (creating it lazily if this is the first
        // save) and copy ONLY the approved preference fields.
        UserSettings settings = userSettingsRepository.findByUser(currentUser)
                .orElseGet(() -> newDefaults(currentUser));

        settings.setDefaultFocusDurationMinutes(settingsDto.defaultFocusDurationMinutes());
        settings.setDefaultSessionType(settingsDto.defaultSessionType());
        settings.setWeeklyStudyGoalMinutes(settingsDto.weeklyStudyGoalMinutes());

        return UserSettingsDto.from(userSettingsRepository.save(settings));
    }

    /**
     * A fresh settings record carrying the documented entity defaults,
     * already linked to the owning user.
     *
     * @param user the owning user
     * @return an unsaved {@link UserSettings}
     */
    private UserSettings newDefaults(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        return settings;
    }

    /**
     * Resolve the current user from the Spring Security context.
     *
     * The principal is a {@code UserDetails} whose username is the login
     * email (StudyFlow uses email as the login identifier).
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
                .orElseThrow(() -> new UserNotFoundException(
                        "Current user could not be resolved."));
    }
}