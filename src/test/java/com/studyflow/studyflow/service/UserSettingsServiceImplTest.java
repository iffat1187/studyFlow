package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.entity.UserSettings;
import com.studyflow.studyflow.exception.InvalidSettingsDataException;
import com.studyflow.studyflow.exception.UserNotFoundException;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.repository.UserSettingsRepository;
import com.studyflow.studyflow.service.impl.UserSettingsServiceImpl;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserSettingsServiceImpl}.
 *
 * The owner is always resolved from the Spring Security context, never from a
 * userId. Covers: lazy default creation, mapping an existing record, the
 * read-only lookup used by feature integrations (never writes), update
 * persisting only the approved preference fields, defensive Bean Validation
 * (zero/negative/oversized values), missing-user resolution, and user
 * isolation between two accounts.
 */
@ExtendWith(MockitoExtension.class)
class UserSettingsServiceImplTest {

    private static final String ALICE_EMAIL = "settings-a@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    private Validator validator;

    private UserSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        service = new UserSettingsServiceImpl(userRepository, userSettingsRepository, validator);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User authenticateAs(String email, Long id) {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        User user = User.builder().id(id).name(email).email(email)
                .password(encoder.encode("password123")).role(Role.USER).build();
        UserDetails details = new org.springframework.security.core.userdetails.User(
                email, user.getPassword(), List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, user.getPassword(),
                        details.getAuthorities()));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private UserSettings settings(User user, int duration, StudySessionType type, int goal) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setDefaultFocusDurationMinutes(duration);
        settings.setDefaultSessionType(type);
        settings.setWeeklyStudyGoalMinutes(goal);
        return settings;
    }

    // TEST 1 — no row yet: defaults are returned AND a defaults row is saved lazily.
    @Test
    void getCurrentUserSettings_noRow_returnsDefaultsAndCreatesRow() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userSettingsRepository.findByUser(alice)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserSettingsDto dto = service.getCurrentUserSettings();

        assertThat(dto).isEqualTo(UserSettingsDto.defaults());
        assertThat(dto.defaultFocusDurationMinutes()).isEqualTo(25);
        assertThat(dto.defaultSessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(dto.weeklyStudyGoalMinutes()).isEqualTo(300);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(alice);
        assertThat(captor.getValue().getDefaultFocusDurationMinutes()).isEqualTo(25);
        assertThat(captor.getValue().getDefaultSessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(captor.getValue().getWeeklyStudyGoalMinutes()).isEqualTo(300);
    }

    // TEST 2 — a persisted row maps to the DTO without any write.
    @Test
    void getCurrentUserSettings_rowExists_mapsRecord() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userSettingsRepository.findByUser(alice))
                .thenReturn(Optional.of(settings(alice, 30, StudySessionType.REVISION, 600)));

        UserSettingsDto dto = service.getCurrentUserSettings();

        assertThat(dto.defaultFocusDurationMinutes()).isEqualTo(30);
        assertThat(dto.defaultSessionType()).isEqualTo(StudySessionType.REVISION);
        assertThat(dto.weeklyStudyGoalMinutes()).isEqualTo(600);
        verify(userSettingsRepository, never()).save(any());
    }

    // TEST 3 — the read-only lookup used by dashboard/timer never writes.
    @Test
    void getSettingsForUser_noRow_returnsDefaultsAndNeverWrites() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userSettingsRepository.findByUser(alice)).thenReturn(Optional.empty());

        UserSettingsDto dto = service.getSettingsForUser(ALICE_EMAIL);

        assertThat(dto).isEqualTo(UserSettingsDto.defaults());
        verify(userSettingsRepository, never()).save(any());
    }

    // TEST 4 — valid update persists ONLY the approved preference fields.
    @Test
    void updateCurrentUserSettings_valid_persistsApprovedFieldsOnly() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        String identity = alice.getEmail();
        String hash = alice.getPassword();
        when(userSettingsRepository.findByUser(alice))
                .thenReturn(Optional.of(settings(alice, 25, StudySessionType.FOCUS, 300)));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserSettingsDto saved = service.updateCurrentUserSettings(
                new UserSettingsDto(30, StudySessionType.PRACTICE, 600));

        assertThat(saved.defaultFocusDurationMinutes()).isEqualTo(30);
        assertThat(saved.defaultSessionType()).isEqualTo(StudySessionType.PRACTICE);
        assertThat(saved.weeklyStudyGoalMinutes()).isEqualTo(600);

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        UserSettings persisted = captor.getValue();
        assertThat(persisted.getDefaultFocusDurationMinutes()).isEqualTo(30);
        assertThat(persisted.getDefaultSessionType()).isEqualTo(StudySessionType.PRACTICE);
        assertThat(persisted.getWeeklyStudyGoalMinutes()).isEqualTo(600);
        // Identity is never touched: email, password hash and role survive.
        assertThat(alice.getEmail()).isEqualTo(identity);
        assertThat(alice.getPassword()).isEqualTo(hash);
        assertThat(alice.getRole()).isEqualTo(Role.USER);
        verify(userRepository, never()).save(any());
    }

    // TEST 5 — invalid goal (zero) is rejected and nothing is persisted.
    @Test
    void updateCurrentUserSettings_zeroGoal_isRejected() {
        authenticateAs(ALICE_EMAIL, 1L);

        assertThatThrownBy(() -> service.updateCurrentUserSettings(
                new UserSettingsDto(25, StudySessionType.FOCUS, 0)))
                .isInstanceOf(InvalidSettingsDataException.class);

        verify(userSettingsRepository, never()).save(any());
    }

    // TEST 6 — invalid values (negative goal, oversized duration) rejected.
    @Test
    void updateCurrentUserSettings_invalidValues_areRejected() {
        authenticateAs(ALICE_EMAIL, 1L);

        assertThatThrownBy(() -> service.updateCurrentUserSettings(
                new UserSettingsDto(25, StudySessionType.FOCUS, -1)))
                .isInstanceOf(InvalidSettingsDataException.class);

        assertThatThrownBy(() -> service.updateCurrentUserSettings(
                new UserSettingsDto(200, StudySessionType.FOCUS, 300)))
                .isInstanceOf(InvalidSettingsDataException.class);

        assertThatThrownBy(() -> service.updateCurrentUserSettings(
                new UserSettingsDto(25, StudySessionType.FOCUS, 10001)))
                .isInstanceOf(InvalidSettingsDataException.class);

        verify(userSettingsRepository, never()).save(any());
    }

    // TEST 7 — first save without an existing row creates the row lazily.
    @Test
    void updateCurrentUserSettings_noRow_createsRowLazily() {
        User alice = authenticateAs(ALICE_EMAIL, 1L);
        when(userSettingsRepository.findByUser(alice)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserSettingsDto saved = service.updateCurrentUserSettings(
                new UserSettingsDto(45, StudySessionType.EXAM_PREPARATION, 900));

        ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
        verify(userSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(alice);
        assertThat(saved.defaultFocusDurationMinutes()).isEqualTo(45);
        assertThat(saved.defaultSessionType()).isEqualTo(StudySessionType.EXAM_PREPARATION);
        assertThat(saved.weeklyStudyGoalMinutes()).isEqualTo(900);
    }

    // TEST 8 — unresolvable principal => UserNotFoundException, never null.
    @Test
    void getCurrentUserSettings_missingUser_throwsUserNotFound() {
        when(userRepository.findByEmail(ALICE_EMAIL)).thenReturn(Optional.empty());
        UserDetails details = new org.springframework.security.core.userdetails.User(
                ALICE_EMAIL, "x", List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "x", details.getAuthorities()));

        assertThatThrownBy(() -> service.getCurrentUserSettings())
                .isInstanceOf(UserNotFoundException.class);
        verify(userSettingsRepository, never()).findByUser(any());
    }

    // TEST 9 — user isolation: each user resolves only their own settings.
    @Test
    void getSettingsForUser_isolation_betweenTwoUsers() {
        String bobEmail = "settings-b@example.com";
        User bob = User.builder().id(2L).name(bobEmail).email(bobEmail)
                .password("x").role(Role.USER).build();
        when(userRepository.findByEmail(bobEmail)).thenReturn(Optional.of(bob));
        when(userSettingsRepository.findByUser(bob))
                .thenReturn(Optional.of(settings(bob, 60, StudySessionType.READING, 1200)));

        UserSettingsDto bobDto = service.getSettingsForUser(bobEmail);

        assertThat(bobDto.defaultFocusDurationMinutes()).isEqualTo(60);
        assertThat(bobDto.defaultSessionType()).isEqualTo(StudySessionType.READING);
        assertThat(bobDto.weeklyStudyGoalMinutes()).isEqualTo(1200);
        verify(userSettingsRepository).findByUser(bob);
    }
}