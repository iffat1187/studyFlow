package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.NotificationPreferenceDto;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.User;

/**
 * Manages a user's notification category preferences.
 *
 * A user has at most one preference record. When no record exists, every
 * category is treated as enabled so notifications work out-of-the-box. A
 * default record is lazily persisted the first time the user views their
 * settings; generation checks preferences without forcing a row to exist.
 *
 * Identity is resolved from the authenticated user (never from the
 * browser), so preferences are inherently user-scoped and cannot be viewed
 * or modified for another user (IDOR prevention).
 */
public interface NotificationPreferenceService {

    /**
     * The authenticated user's preferences, creating and persisting a
     * default (all-enabled) record the first time this is called.
     *
     * @param userEmail the authenticated user's email
     * @return the user's preferences (never null for a known user)
     */
    NotificationPreferenceDto getPreferences(String userEmail);

    /**
     * Persist the authenticated user's preferences. Creates a default record
     * first when the user has none.
     *
     * @param userEmail the authenticated user's email
     * @param dto       the preferences to save (null fields stay untouched,
     *                  absent/off toggles default to disabled)
     */
    void updatePreferences(String userEmail, NotificationPreferenceDto dto);

    /**
     * Whether the given notification category should be generated for the
     * user. Returns true when the user has no preference record (default
     * enabled) without persisting anything.
     *
     * @param user the owner (already resolved by the generation service)
     * @param type the notification category
     * @return true when generation for that category is enabled
     */
    boolean isEnabled(User user, NotificationType type);
}
