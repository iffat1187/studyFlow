package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.UserSettingsDto;

/**
 * Per-user study preferences (default focus duration, default session type,
 * weekly study goal).
 *
 * The authenticated user is resolved by the {@code current...} methods from
 * the Spring Security context — the caller never supplies a userId, hidden
 * field, URL parameter or email. This keeps every read and write on the
 * caller's own record, so one user can never read or modify another user's
 * settings (IDOR prevention).
 *
 * The settings record is optional: while {@link #getCurrentUserSettings()}
 * lazily creates a row the first time Settings is opened, the read-only
 * {@link #getSettingsForUser(String)} used by feature integrations never
 * writes and simply reports the documented defaults when no row exists.
 */
public interface UserSettingsService {

    /**
     * The current authenticated user's settings, creating a defaults row
     * lazily the first time settings are requested/opened.
     *
     * @return the settings DTO (never null)
     */
    UserSettingsDto getCurrentUserSettings();

    /**
     * Read-only settings lookup for a resolved user's email (used by feature
     * integrations such as the dashboard and the study-session flow).
     *
     * Never writes: a user without a row simply receives the documented
     * defaults, so rendering a page can never trigger persistence.
     *
     * @param userEmail the authenticated user's email
     * @return the settings DTO (never null)
     */
    UserSettingsDto getSettingsForUser(String userEmail);

    /**
     * Update the current authenticated user's settings.
     *
     * Only the approved preference fields are copied; identity, credentials,
     * role and notification preferences are never touched. Defensively
     * validates the submitted DTO before persisting.
     *
     * @param dto the submitted settings
     * @return the persisted settings DTO
     */
    UserSettingsDto updateCurrentUserSettings(UserSettingsDto dto);
}