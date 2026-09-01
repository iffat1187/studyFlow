package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.ProfileDto;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.exception.InvalidProfileDataException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ProfileService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserRepository userRepository;
    private final Validator validator;

    /**
     * Constructor injection.
     *
     * @param userRepository resolves the current user by the authenticated email
     * @param validator      performs Bean Validation on submitted profile data
     */
    public ProfileServiceImpl(UserRepository userRepository, Validator validator) {
        this.userRepository = userRepository;
        this.validator = validator;
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
