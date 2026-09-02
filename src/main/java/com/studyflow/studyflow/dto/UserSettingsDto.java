package com.studyflow.studyflow.dto;

import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.UserSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The authenticated user's study preferences, shown on and editable from
 * the Settings page.
 *
 * Validation mirrors what the features accept: the focus duration stays
 * within the Focus Timer's MIN/MAX bounds, the session type is a known enum
 * value, and the weekly goal is a positive number with a sensible upper
 * bound. Identity (email), credentials (password) and role are NOT part of
 * settings — they stay on the {@link com.studyflow.studyflow.entity.User}
 * record and are never editable here.
 */
public record UserSettingsDto(
        @NotNull(message = "Default focus duration is required.")
        @Min(value = 5, message = "Default focus duration must be at least 5 minutes.")
        @Max(value = 180, message = "Default focus duration cannot exceed 180 minutes.")
        Integer defaultFocusDurationMinutes,

        @NotNull(message = "Default session type is required.")
        StudySessionType defaultSessionType,

        @NotNull(message = "Weekly study goal is required.")
        @Min(value = 1, message = "Weekly study goal must be at least 1 minute.")
        @Max(value = 10000, message = "Weekly study goal cannot exceed 10000 minutes.")
        Integer weeklyStudyGoalMinutes
) {

    /**
     * Map persisted settings onto the form DTO.
     *
     * @param settings the persisted record
     * @return the DTO backing the Settings form
     */
    public static UserSettingsDto from(UserSettings settings) {
        return new UserSettingsDto(
                settings.getDefaultFocusDurationMinutes(),
                settings.getDefaultSessionType(),
                settings.getWeeklyStudyGoalMinutes()
        );
    }

    /**
     * The documented defaults used when the user has no settings row yet.
     *
     * @return a DTO carrying the entity defaults
     */
    public static UserSettingsDto defaults() {
        return new UserSettingsDto(
                UserSettings.DEFAULT_FOCUS_DURATION_MINUTES,
                UserSettings.DEFAULT_SESSION_TYPE,
                UserSettings.DEFAULT_WEEKLY_STUDY_GOAL_MINUTES
        );
    }
}