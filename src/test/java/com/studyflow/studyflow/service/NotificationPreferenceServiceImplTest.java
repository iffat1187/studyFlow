package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.NotificationPreferenceDto;
import com.studyflow.studyflow.entity.NotificationPreference;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationPreferenceRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.impl.NotificationPreferenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationPreferenceServiceImpl}.
 *
 * Verifies the three contracts: (1) a user with no preference record is
 * treated as fully enabled without persisting anything (isEnabled), (2) a
 * default all-enabled row is lazily created on first settings view, and (3)
 * updatePreferences persists exactly what the submitted DTO contains.
 */
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceImplTest {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationPreferenceService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceServiceImpl(preferenceRepository, userRepository);
        user = User.builder().id(1L).name("Alice").email("alice@example.com")
                .password("encoded").role(Role.USER).build();
    }

    @Test
    void noRecord_isEnabled_trueForEveryType_withoutPersisting() {
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThat(service.isEnabled(user, NotificationType.TASK_DUE)).isTrue();
        assertThat(service.isEnabled(user, NotificationType.STREAK_REMINDER)).isTrue();
        assertThat(service.isEnabled(user, NotificationType.SYSTEM)).isTrue();

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void disabledRecord_isEnabled_respectsFlag() {
        NotificationPreference prefs = new NotificationPreference();
        prefs.setUser(user);
        prefs.setTaskDueEnabled(false);
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.of(prefs));

        assertThat(service.isEnabled(user, NotificationType.TASK_DUE)).isFalse();
        assertThat(service.isEnabled(user, NotificationType.TASK_OVERDUE)).isTrue();
    }

    @Test
    void getPreferences_withNoRecord_persistsAllEnabledDefault() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.empty());

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        when(preferenceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceDto dto = service.getPreferences("alice@example.com");

        assertThat(dto).isNotNull();
        assertThat(dto.taskDueEnabled()).isTrue();
        assertThat(dto.systemEnabled()).isTrue();

        NotificationPreference saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.isTaskDueEnabled()).isTrue();
    }

    @Test
    void getPreferences_existingRecord_returnsItWithoutSaving() {
        NotificationPreference prefs = new NotificationPreference();
        prefs.setUser(user);
        prefs.setTaskDueEnabled(false);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.of(prefs));

        NotificationPreferenceDto dto = service.getPreferences("alice@example.com");

        assertThat(dto.taskDueEnabled()).isFalse();
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void updatePreferences_createsRecord_applysDto() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.empty());

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        when(preferenceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.updatePreferences("alice@example.com",
                new NotificationPreferenceDto(true, false, true, true, false, true, false));

        NotificationPreference saved = captor.getValue();
        assertThat(saved.isTaskDueEnabled()).isTrue();
        assertThat(saved.isTaskOverdueEnabled()).isFalse();
        assertThat(saved.isStudyReminderEnabled()).isFalse();
        assertThat(saved.isSystemEnabled()).isFalse();
    }

    @Test
    void nullUserEmail_isEnabledFalseAndNoLookup() {
        assertThat(service.isEnabled(null, NotificationType.TASK_DUE)).isFalse();
        verify(preferenceRepository, never()).findByUser(any());
    }
}
