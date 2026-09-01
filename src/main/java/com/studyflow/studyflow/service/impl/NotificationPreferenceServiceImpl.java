package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.NotificationPreferenceDto;
import com.studyflow.studyflow.entity.NotificationPreference;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationPreferenceRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link NotificationPreferenceService}.
 *
 * Every operation is scoped to the user resolved from the authenticated
 * email, so one user can never read or modify another user's preferences.
 * A missing preference row is treated as "all enabled"; a default row is
 * created the first time the user views or saves their settings.
 */
@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    /**
     * Constructor injection.
     */
    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository preferenceRepository,
                                             UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public NotificationPreferenceDto getPreferences(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return NotificationPreferenceDto.from(null); // not expected for a real user
        }
        NotificationPreference existing = preferenceRepository.findByUser(user).orElse(null);
        if (existing != null) {
            return NotificationPreferenceDto.from(existing);
        }
        // Lazy default: persist an all-enabled record on first settings view.
        return NotificationPreferenceDto.from(preferenceRepository.save(newPreference(user)));
    }

    @Override
    @Transactional
    public void updatePreferences(String userEmail, NotificationPreferenceDto dto) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return;
        }
        NotificationPreference prefs = preferenceRepository.findByUser(user)
                .orElseGet(() -> preferenceRepository.save(newPreference(user)));
        apply(prefs, dto);
        preferenceRepository.save(prefs);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(User user, NotificationType type) {
        if (user == null) {
            return false;
        }
        return preferenceRepository.findByUser(user)
                .map(prefs -> prefs.isEnabled(type))
                .orElse(true); // no record => everything enabled by default
    }

    private NotificationPreference newPreference(User user) {
        NotificationPreference prefs = new NotificationPreference();
        prefs.setUser(user);
        return prefs; // all booleans default to true
    }

    /**
     * Copy the submitted DTO onto the entity. A null field (unchecked in the
     * form, or omitted) is treated as disabled unless it was defaulted. The
     * form always submits every toggle (off toggles arrive as false), so null
     * -> false is the safe behavior.
     */
    private void apply(NotificationPreference prefs, NotificationPreferenceDto dto) {
        prefs.setTaskDueEnabled(Boolean.TRUE.equals(dto.taskDueEnabled()));
        prefs.setTaskOverdueEnabled(Boolean.TRUE.equals(dto.taskOverdueEnabled()));
        prefs.setExamUpcomingEnabled(Boolean.TRUE.equals(dto.examUpcomingEnabled()));
        prefs.setExamTodayEnabled(Boolean.TRUE.equals(dto.examTodayEnabled()));
        prefs.setStudyReminderEnabled(Boolean.TRUE.equals(dto.studyReminderEnabled()));
        prefs.setStreakReminderEnabled(Boolean.TRUE.equals(dto.streakReminderEnabled()));
        prefs.setSystemEnabled(Boolean.TRUE.equals(dto.systemEnabled()));
    }

    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }
}
