package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase G tests: the Focus Timer persistence endpoint
 * (POST /study-sessions/from-timer).
 *
 * Covers: a successful completed run turning into a normal StudySession,
 * subject-level sessions (no topic), server-side ownership rejection of
 * foreign subjects / topics / Topic↔Subject mismatches, request validation
 * (duration, times, required fields), duplicate-submission idempotency,
 * and the authenticated-route requirement.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudySessionTimerFlowTest {

    private static final String ENDPOINT = "/study-sessions/from-timer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private TopicRepository topicRepository;

    @MockitoBean
    private StudySessionRepository studySessionRepository;

    private User buildUser(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    private User userA() {
        return buildUser(1L, "User A", "a@example.com");
    }

    private User userB() {
        return buildUser(2L, "User B", "b@example.com");
    }

    private Subject subject(Long id, String name, User owner) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setDescription("Owned by " + owner.getEmail());
        subject.setColor("Purple");
        subject.setUser(owner);
        return subject;
    }

    private Topic topic(Long id, String name, Subject subject) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setSubject(subject);
        return topic;
    }

    private void stubUser(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private void stubOwnedSubject(User user, Subject subject) {
        stubUser(user);
        when(subjectRepository.findByIdAndUser(eq(subject.getId()), eq(user)))
                .thenReturn(Optional.of(subject));
    }

    private void stubTopicBelongsToSubject(Topic topic, Subject subject) {
        when(topicRepository.findByIdAndSubject(eq(topic.getId()), eq(subject)))
                .thenReturn(Optional.of(topic));
    }

    private void stubSaveReturnsArgument() {
        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private String validBody(String clientKey) {
        return """
                {
                  "subjectId": 100,
                  "topicId": 200,
                  "sessionType": "FOCUS",
                  "sessionDate": "2026-08-30",
                  "startTime": "08:00:00",
                  "endTime": "08:25:00",
                  "durationMinutes": 25,
                  "notes": null,
                  "clientKey": "%s"
                }
                """.formatted(clientKey);
    }

    // TEST 1 — a completed timer (subject + topic) creates a real session.
    @Test
    void fromTimer_successWithTopic_createsSessionWithOwnedSubjectAndTopic() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        Topic topic = topic(200L, "Spring Security", subject);
        stubOwnedSubject(a, subject);
        stubTopicBelongsToSubject(topic, subject);
        stubSaveReturnsArgument();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("timer-key-success-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Study session saved successfully!"));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        StudySession saved = captor.getValue();
        assertThat(saved.getSubject()).isSameAs(subject);
        assertThat(saved.getTopic()).isSameAs(topic);
        assertThat(saved.getSessionDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(8, 25));
        assertThat(saved.getDurationMinutes()).isEqualTo(25);
        assertThat(saved.getSessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(saved.getNotes()).isNull();
    }

    // TEST 2 — actual active duration is persisted (never the preset).
    @Test
    void fromTimer_persistsActualDurationNotPreset() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubSaveReturnsArgument();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:32:14",
                                  "durationMinutes": 32,
                                  "notes": null,
                                  "clientKey": "timer-key-duration-2"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        assertThat(captor.getValue().getDurationMinutes()).isEqualTo(32);
        assertThat(captor.getValue().getTopic()).isNull();
    }

    // TEST 3 — a REVISION session persists the selected enum type.
    @Test
    void fromTimer_revisionType_persistsRevision() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubSaveReturnsArgument();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "REVISION",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-revision-3b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionType()).isEqualTo(StudySessionType.REVISION);
    }

    // TEST 4 — no topic → topic_id stays NULL (subject-level session).
    @Test
    void fromTimer_withoutTopic_savesNullTopic() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubSaveReturnsArgument();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-notopic-3"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isNull();
        assertThat(captor.getValue().getSubject()).isSameAs(subject);
    }

    // TEST 5 — a topic id that belongs to NO subject of this user is rejected (no session).
    @Test
    void fromTimer_foreignTopic_returns403() throws Exception {
        User a = userA();
        User b = userB();
        Subject subjectA = subject(100L, "Subject A", a);
        Subject subjectB = subject(300L, "Subject B", b);
        Topic foreignTopic = topic(400L, "Foreign Topic", subjectB);
        stubOwnedSubject(a, subjectA);
        stubUser(a);
        when(topicRepository.findByIdAndSubject(eq(foreignTopic.getId()), eq(subjectA)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": 400,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-foreign-topic-4"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 6 — a topic that belongs to a DIFFERENT subject is rejected.
    @Test
    void fromTimer_topicSubjectMismatch_returns403() throws Exception {
        User a = userA();
        Subject subjectOne = subject(100L, "Subject One", a);
        Subject subjectTwo = subject(300L, "Subject Two", a);
        Topic topicOfSubjectTwo = topic(400L, "Topic of Subject Two", subjectTwo);
        stubOwnedSubject(a, subjectOne);
        stubUser(a);
        when(topicRepository.findByIdAndSubject(eq(topicOfSubjectTwo.getId()), eq(subjectOne)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": 400,
                                  "sessionType": "REVISION",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-mismatch-5"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 7 — a foreign subject id is rejected (no session, no query on topics).
    @Test
    void fromTimer_foreignSubject_returns403() throws Exception {
        User a = userA();
        User b = userB();
        Subject subjectOfB = subject(300L, "Subject B", b);
        stubUser(a);
        when(subjectRepository.findByIdAndUser(eq(subjectOfB.getId()), eq(a)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 300,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-foreign-subject-6"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 8 — durationMinutes <= 0 is rejected.
    @Test
    void fromTimer_zeroDuration_returns400() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 0,
                                  "notes": null,
                                  "clientKey": "timer-key-zero-duration-7"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 9 — an impossible duration (over 24h) is rejected.
    @Test
    void fromTimer_durationOverMaximum_returns400() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 1441,
                                  "notes": null,
                                  "clientKey": "timer-key-overmax-8"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 10 — end time before start time is rejected.
    @Test
    void fromTimer_endBeforeStart_returns400() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:25:00",
                                  "endTime": "08:00:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-end-before-start-9"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("End time cannot be before start time."));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 11 — a missing subject id fails validation (400, not 500/403).
    @Test
    void fromTimer_missingSubject_returns400() throws Exception {
        User a = userA();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": null,
                                  "topicId": null,
                                  "sessionType": "FOCUS",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-no-subject-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 12 — an unrecognized session type is rejected (400).
    @Test
    void fromTimer_invalidSessionType_returns400() throws Exception {
        User a = userA();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": 100,
                                  "topicId": null,
                                  "sessionType": "NOT_A_REAL_TYPE",
                                  "sessionDate": "2026-08-30",
                                  "startTime": "08:00:00",
                                  "endTime": "08:25:00",
                                  "durationMinutes": 25,
                                  "notes": null,
                                  "clientKey": "timer-key-bad-type-11"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 13 — replaying the same clientKey creates only ONE session (409 on the second).
    @Test
    void fromTimer_duplicateClientKey_onlyOneSession() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubTopicBelongsToSubject(topic(200L, "Spring Security", subject), subject);
        stubSaveReturnsArgument();
        stubUser(a);
        String body = validBody("timer-key-duplicate-12");

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        verify(studySessionRepository).save(any(StudySession.class)); // exactly once
    }

    // TEST 14 — a replay with a DIFFERENT clientKey is a legitimate NEW session.
    @Test
    void fromTimer_differentClientKeys_twoSessions() throws Exception {
        User a = userA();
        Subject subject = subject(100L, "Spring Boot", a);
        stubOwnedSubject(a, subject);
        stubTopicBelongsToSubject(topic(200L, "Spring Security", subject), subject);
        stubSaveReturnsArgument();
        stubUser(a);

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("timer-key-run-one")))
                .andExpect(status().isOk());

        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .with(user(a.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("timer-key-run-two")))
                .andExpect(status().isOk());

        verify(studySessionRepository, org.mockito.Mockito.times(2))
                .save(any(StudySession.class));
    }

    // TEST 15 — the endpoint requires authentication (anonymous → login).
    @Test
    void fromTimer_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("timer-key-anon")))
                .andExpect(status().is3xxRedirection());

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }
}