package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.service.ProfileService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current user's profile picture name to the shared layout.
 *
 * The top navbar (and the Profile page) render the user's uploaded avatar.
 * Because both live inside the global layout rendered on every
 * authenticated view, this advice injects the current user's stored picture
 * file name once per request as {@code currentProfileImageName} (null when
 * the user has no picture, so the default avatar is shown).
 *
 * The lookup is defensive: an authenticated principal that does not resolve
 * to a persisted {@code User} (e.g. a transient/removed account) yields
 * {@code null} — the avatar degrades to the default icon instead of failing
 * the whole page.
 */
@ControllerAdvice
public class ProfileNavbarAdvice {

    private final ProfileService profileService;

    /**
     * Constructor injection.
     */
    public ProfileNavbarAdvice(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * The authenticated user's stored profile picture file name.
     *
     * @param authentication security context (anonymous on public pages)
     * @return the stored file name, or null when anonymous, no picture, or
     *         the principal cannot be resolved to a user
     */
    @ModelAttribute("currentProfileImageName")
    public String currentProfileImageName(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return profileService.getCurrentUserProfileImageName();
        } catch (UserNotFoundException e) {
            return null;
        }
    }
}