package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.StudySessionDto;
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
import com.studyflow.studyflow.service.impl.StudySessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase B service-level ownership tests (TESTS 1-12).
 *
 * Directly exercises {@link StudySessionServiceImpl} with mocked
 * repositories so the business rules can be verified without a browser:
 * subject ownership, optional-topic ownership and Topic↔Subject consistency
 * are all enforced server-side through the authenticated user's email.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudySessionServiceTest {

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StudySessionServiceImpl studySessionService;

    private User userA;
    private User userB;
    private Subject subjectA;
    private Subject subjectA2;
    private Subject subjectB;
    private Topic topicA;
    private Topic topicA2;
    private Topic topicB;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(1L).name("User A").email("a@example.com")
                .password("encoded").role(Role.USER).build();
        userB = User.builder().id(2L).name("User B").email("b@example.com")
                .password("encoded").role(Role.USER).build();

        subjectA = subject(100L, "Spring Boot", userA);
        subjectA2 = subject(150L, "Algorithm", userA);
        subjectB = subject(200L, "Spring Boot B", userB);

        topicA = topic(1000L, "Spring Security", subjectA);
        topicA2 = topic(1001L, "Graphs", subjectA2);
        topicB = topic(2000L, "B Topic", subjectB);

        // Current-user resolution (Mockito matcher empties otherwise).
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(userA));
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB));

        // Subject ownership lookups.
        when(subjectRepository.findByIdAndUser(100L, userA)).thenReturn(Optional.of(subjectA));
        when(subjectRepository.findByIdAndUser(150L, userA)).thenReturn(Optional.of(subjectA2));
        when(subjectRepository.findByIdAndUser(200L, userB)).thenReturn(Optional.of(subjectB));

        // Topic-within-subject lookups (single query enforces ownership + consistency).
        when(topicRepository.findByIdAndSubject(1000L, subjectA)).thenReturn(Optional.of(topicA));
        when(topicRepository.findByIdAndSubject(1001L, subjectA2)).thenReturn(Optional.of(topicA2));
        when(topicRepository.findByIdAndSubject(2000L, subjectB)).thenReturn(Optional.of(topicB));

        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Subject subject(Long id, String name, User user) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setUser(user);
        return subject;
    }

    private Topic topic(Long id, String name, Subject subject) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setSubject(subject);
        return topic;
    }

    private StudySessionDto dto(Long subjectId, Long topicId, StudySessionType type,
                                LocalDate date, Integer duration, String notes) {
        return new StudySessionDto(
                null,
                subjectId,
                topicId,
                null, // read-only display value, never used for ownership
                null, // read-only display value, never used for ownership
                date,
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                duration,
                type,
                notes
        );
    }

    private StudySession session(Long id, Subject subject, Topic topic,
                                 StudySessionType type, Integer duration) {
        StudySession session = new StudySession();
        session.setId(id);
        session.setSubject(subject);
        session.setTopic(topic);
        session.setSessionDate(LocalDate.now());
        session.setSessionType(type);
        session.setDurationMinutes(duration);
        return session;
    }

    // TESTS 1 + 12 — create with a subject and NO topic (optional topic accepted).
    @Test
    void createSession_subjectOnly_savesAndTopicStaysNull() {
        StudySessionDto result = studySessionService.saveSession(
                dto(100L, null, StudySessionType.FOCUS, LocalDate.now(), 60, "Deep work"),
                "a@example.com");

        assertThat(result.subjectName()).isEqualTo("Spring Boot");
        assertThat(result.topicId()).isNull();
        assertThat(result.topicName()).isNull();
        assertThat(result.sessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(result.durationMinutes()).isEqualTo(60);

        ArgumentCaptor<StudySession> saved = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(saved.capture());
        assertThat(saved.getValue().getSubject()).isSameAs(subjectA);
        assertThat(saved.getValue().getTopic()).isNull();
        assertThat(saved.getValue().getSessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(saved.getValue().getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getValue().getSessionDate()).isEqualTo(LocalDate.now());
    }

    // TEST 2 — create with a subject AND a matching topic.
    @Test
    void createSession_subjectAndTopic_saves() {
        StudySessionDto result = studySessionService.saveSession(
                dto(100L, 1000L, StudySessionType.REVISION, LocalDate.now(), 90, "Revise notes"),
                "a@example.com");

        assertThat(result.subjectName()).isEqualTo("Spring Boot");
        assertThat(result.topicName()).isEqualTo("Spring Security");

        ArgumentCaptor<StudySession> saved = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(saved.capture());
        assertThat(saved.getValue().getSubject()).isSameAs(subjectA);
        assertThat(saved.getValue().getTopic()).isSameAs(topicA);
    }

    // TEST 3 — another user's subject is rejected and nothing is saved.
    @Test
    void createSession_anotherUsersSubject_rejected() {
        assertThatThrownBy(() -> studySessionService.saveSession(
                dto(100L, null, StudySessionType.FOCUS, LocalDate.now(), 60, null),
                "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 4 — another user's topic is rejected even with an owned subject.
    @Test
    void createSession_anotherUsersTopic_rejected() {
        assertThatThrownBy(() -> studySessionService.saveSession(
                dto(200L, 1000L, StudySessionType.READING, LocalDate.now(), 45, null),
                "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 5 — a topic owned by the user but belonging to a DIFFERENT subject is rejected.
    @Test
    void createSession_topicFromDifferentSubject_rejected() {
        assertThatThrownBy(() -> studySessionService.saveSession(
                dto(100L, 1001L, StudySessionType.ASSIGNMENT, LocalDate.now(), 120, null),
                "a@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 6 — the user receives exactly their own sessions via the user-scoped query.
    @Test
    void getAllSessions_returnsOnlyOwnSessions() {
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(userA))
                .thenReturn(List.of(
                        session(1L, subjectA, null, StudySessionType.FOCUS, 45),
                        session(2L, subjectA, topicA, StudySessionType.REVISION, 60),
                        session(3L, subjectA2, null, StudySessionType.READING, 30)
                ));

        List<StudySessionDto> result = studySessionService.getAllSessions("a@example.com");

        assertThat(result).hasSize(3);
        assertThat(result.get(1).topicName()).isEqualTo("Spring Security");

        // Scoped to the authenticated user in the database — never findAll().
        verify(studySessionRepository).findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(userA);
        verify(studySessionRepository, never()).findAll();
    }

    // TEST 7 — loading another user's session is a safe denial.
    @Test
    void getSessionById_anotherUsersSession_rejected() {
        assertThatThrownBy(() -> studySessionService.getSessionById(99L, "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // TEST 8 — the owner updates their own session.
    @Test
    void updateSession_ownSession_updatesFields() {
        StudySession existing = session(1L, subjectA, topicA, StudySessionType.FOCUS, 60);
        when(studySessionRepository.findByIdAndSubject_User(1L, userA))
                .thenReturn(Optional.of(existing));

        StudySessionDto result = studySessionService.updateSession(
                1L,
                dto(100L, 1000L, StudySessionType.PRACTICE, LocalDate.now(), 90, "Updated notes"),
                "a@example.com");

        verify(studySessionRepository).save(existing);
        assertThat(existing.getSessionType()).isEqualTo(StudySessionType.PRACTICE);
        assertThat(existing.getDurationMinutes()).isEqualTo(90);
        assertThat(existing.getNotes()).isEqualTo("Updated notes");
        assertThat(existing.getSubject()).isSameAs(subjectA);
        assertThat(existing.getTopic()).isSameAs(topicA);
        assertThat(result.sessionType()).isEqualTo(StudySessionType.PRACTICE);
        assertThat(result.durationMinutes()).isEqualTo(90);
    }

    // TEST 9 — updating another user's session is rejected.
    @Test
    void updateSession_anotherUsersSession_rejected() {
        assertThatThrownBy(() -> studySessionService.updateSession(
                1L,
                dto(100L, null, StudySessionType.FOCUS, LocalDate.now(), 60, null),
                "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(studySessionRepository, never()).save(any(StudySession.class));
    }

    // TEST 10 — the owner deletes their own session.
    @Test
    void deleteSession_ownSession_succeeds() {
        StudySession existing = session(1L, subjectA, null, StudySessionType.READING, 30);
        when(studySessionRepository.findByIdAndSubject_User(1L, userA))
                .thenReturn(Optional.of(existing));

        studySessionService.deleteSession(1L, "a@example.com");

        verify(studySessionRepository).delete(existing);
    }

    // TEST 11 — deleting another user's session is rejected.
    @Test
    void deleteSession_anotherUsersSession_rejected() {
        assertThatThrownBy(() -> studySessionService.deleteSession(1L, "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(studySessionRepository, never()).delete(any(StudySession.class));
    }
}