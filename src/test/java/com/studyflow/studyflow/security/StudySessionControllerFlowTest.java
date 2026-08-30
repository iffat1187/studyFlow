package com.studyflow.studyflow.security;

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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase C + D tests enforcing the Study Session controller flow.
 *
 * Covers: the list view (empty state and real sessions), the create modal deep
 * link, user isolation on the list, the create form only exposing the
 * authenticated user's subjects and (subject-grouped) topics, valid saves via
 * Post/Redirect/Get, validation failures re-rendering the list view with the
 * modal open and submitted values preserved, and IDOR rejection of foreign
 * subject/topic ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudySessionControllerFlowTest {

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

    private Subject ownedSubject(Long id, String name, User owner) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setDescription("Owned by " + owner.getEmail());
        subject.setColor("Purple");
        subject.setUser(owner);
        return subject;
    }

    private Topic ownedTopic(Long id, String name, Subject subject) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setSubject(subject);
        return topic;
    }

    private StudySession session(Long id, Subject subject, Topic topic, LocalDate date,
                                 LocalTime start, LocalTime end, Integer duration,
                                 StudySessionType type, String notes) {
        StudySession s = new StudySession();
        s.setId(id);
        s.setSubject(subject);
        s.setTopic(topic);
        s.setSessionDate(date);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setDurationMinutes(duration);
        s.setSessionType(type);
        s.setNotes(notes);
        return s;
    }

    private void stubUser(User user) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private void stubOwnSubjects(User user, List<Subject> ownSubjects) {
        stubUser(user);
        when(subjectRepository.findAllByUserOrderByIdAsc(user)).thenReturn(ownSubjects);
    }

    // TEST 1 — the list page resolves and shows a useful empty state.
    @Test
    void getList_empty_showsEmptyState() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of());

        mockMvc.perform(get("/study-sessions")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sessions", "subjects", "topicsBySubject", "studySessionDto"))
                .andExpect(model().attribute("showSessionModal", false))
                .andExpect(content().string(containsString("Study Sessions")))
                .andExpect(content().string(containsString("No study sessions yet.")))
                .andExpect(content().string(containsString("Start Study Session")))
                .andExpect(content().string(not(containsString("Database"))));

        // The session list uses the user-scoped query only.
        verify(studySessionRepository).findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a);
    }

    // TEST 2 — ?add=true opens the create modal on the list page.
    @Test
    void getList_addTrue_opensCreateModal() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of());

        mockMvc.perform(get("/study-sessions").param("add", "true")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showSessionModal", true))
                .andExpect(content().string(containsString("Start Study Session")))
                .andExpect(content().string(containsString("session-modal-overlay open")));
    }

    // TEST 3 — the deep link /study-sessions/new opens the modal and exposes
    // only the authenticated user's subjects and their subject-grouped topics.
    @Test
    void getNew_opensModalAndExposesOwnSubjectsAndTopics() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Subject subjectA2 = ownedSubject(26L, "Database", a);
        Subject subjectB = ownedSubject(30L, "B Subject", userB());
        stubOwnSubjects(a, List.of(subjectA, subjectA2));
        when(topicRepository.findBySubject_UserOrderByIdAsc(a))
                .thenReturn(List.of(
                        ownedTopic(1000L, "Spring Security", subjectA),
                        ownedTopic(1001L, "SQL", subjectA2)));
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of());

        mockMvc.perform(get("/study-sessions/new")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("showSessionModal", true))
                .andExpect(model().attribute("topicsBySubject", hasKey(25L)))
                .andExpect(model().attribute("topicsBySubject", hasKey(26L)))
                .andExpect(content().string(containsString("Study Sessions")))
                .andExpect(content().string(containsString("Start Study Session")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("Database")))
                .andExpect(content().string(containsString("Spring Security")))
                .andExpect(content().string(containsString("SQL")))
                .andExpect(content().string(not(containsString("B Subject"))));

        // Subject dropdown comes from the user-scoped query.
        verify(subjectRepository).findAllByUserOrderByIdAsc(a);
        // Embedded topic grouping comes from the user-scoped query.
        verify(topicRepository).findBySubject_UserOrderByIdAsc(a);
    }

    // TEST 4 — the list renders real sessions with accurate values.
    @Test
    void getList_rendersCurrentUsersSessions() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Topic topicA = ownedTopic(1000L, "Spring Security", subjectA);
        stubOwnSubjects(a, List.of(subjectA));
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of(
                        session(1L, subjectA, topicA, LocalDate.of(2026, 8, 29),
                                LocalTime.of(9, 0), LocalTime.of(10, 0), 60,
                                StudySessionType.FOCUS, "Reviewed auth flow"),
                        session(2L, subjectA, null, LocalDate.of(2026, 8, 30),
                                null, null, 90, StudySessionType.REVISION, null)));

        mockMvc.perform(get("/study-sessions")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sessions"))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("Spring Security")))
                .andExpect(content().string(containsString("Focus")))
                .andExpect(content().string(containsString("Revision")))
                .andExpect(content().string(containsString("9:00 AM – 10:00 AM")))
                .andExpect(content().string(containsString("Aug 29, 2026")))
                .andExpect(content().string(containsString("1h 30m")))
                .andExpect(content().string(containsString("Reviewed auth flow")))
                .andExpect(content().string(containsString("Time not set")))
                // Sub-Phase E: history is grouped by month, newest first.
                .andExpect(content().string(containsString("August 2026")))
                .andExpect(content().string(containsString("2 sessions")));
    }

    // TEST 5 — user B only ever sees their own sessions on the list.
    @Test
    void getList_userB_seesOnlyOwnSessions() throws Exception {
        User b = userB();
        Subject subjectB = ownedSubject(30L, "B Subject", b);
        stubOwnSubjects(b, List.of(subjectB));
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(b))
                .thenReturn(List.of(
                        session(9L, subjectB, null, LocalDate.of(2026, 8, 30),
                                LocalTime.of(14, 0), LocalTime.of(15, 30), 90,
                                StudySessionType.PRACTICE, "B notes")));

        mockMvc.perform(get("/study-sessions")
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("B Subject")))
                .andExpect(content().string(containsString("B notes")))
                .andExpect(content().string(not(containsString("a@example.com"))))
                .andExpect(content().string(not(containsString("Reviewed auth flow"))));

        verify(studySessionRepository).findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(b);
        verify(studySessionRepository, never()).findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(userA());
    }

    // TEST 6 — anonymous access to the list redirects to login.
    @Test
    void getList_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/study-sessions"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void getNew_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/study-sessions/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void save_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/study-sessions/save").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // TEST 7 — create with subject only (no topic), redirect via P/R/G.
    @Test
    void save_subjectOnly_createsSessionAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "25")
                        .param("topicId", "")
                        .param("sessionDate", "2026-08-29")
                        .param("startTime", "09:00")
                        .param("endTime", "10:00")
                        .param("durationMinutes", "60")
                        .param("sessionType", "FOCUS")
                        .param("notes", "")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study-sessions"))
                .andExpect(flash().attribute("successMessage", "Study session created successfully!"));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());

        StudySession saved = captor.getValue();
        assertThat(saved.getSubject()).isSameAs(subjectA);
        assertThat(saved.getTopic()).isNull();
        assertThat(saved.getSessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(saved.getSessionDate()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
    }

    // TEST 8 — create with subject AND a matching topic.
    @Test
    void save_subjectAndTopic_createsSessionWithTopic() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Topic topicA = ownedTopic(1000L, "Spring Security", subjectA);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        when(topicRepository.findByIdAndSubject(eq(1000L), any())).thenReturn(Optional.of(topicA));
        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "25")
                        .param("topicId", "1000")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "45")
                        .param("sessionType", "REVISION")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study-sessions"));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isSameAs(subjectA);
        assertThat(captor.getValue().getTopic()).isSameAs(topicA);
    }

    // TEST 9 — a non-existent subject is a safe failure.
    @Test
    void save_nonExistentSubject_isForbiddenAndNotSaved() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // No subject id 999 belongs to any user in this test world.
        when(subjectRepository.findByIdAndUser(eq(999L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "999")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .param("sessionType", "FOCUS")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 10 — another user's subject is rejected.
    @Test
    void save_anotherUsersSubject_isForbiddenAndNotSaved() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // Subject 30 belongs to User B: the user-scoped lookup returns empty for A.
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "30")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .param("sessionType", "FOCUS")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 11 — another user's topic is rejected even with an owned subject.
    @Test
    void save_anotherUsersTopic_isForbiddenAndNotSaved() throws Exception {
        User b = userB();
        Subject subjectB = ownedSubject(30L, "B Subject", b);
        stubOwnSubjects(b, List.of(subjectB));
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.of(subjectB));
        // Topic 1000 belongs to User A's subject: never matches B's subject.
        when(topicRepository.findByIdAndSubject(eq(1000L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "30")
                        .param("topicId", "1000")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .param("sessionType", "FOCUS")
                        .with(csrf())
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 12 — a topic from a DIFFERENT (even owned) subject is rejected.
    @Test
    void save_topicFromDifferentSubject_isForbiddenAndNotSaved() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        // Topic 1001 belongs to User A's OTHER subject (26): never matches subject 25.
        when(topicRepository.findByIdAndSubject(eq(1001L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "25")
                        .param("topicId", "1001")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .param("sessionType", "FOCUS")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 13a — missing required fields produce validation errors; the list
    // re-renders with the modal open and the submitted values preserved.
    @Test
    void save_missingRequiredFields_reRendersListWithModalOpen() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "")
                        .param("topicId", "")
                        .param("sessionDate", "")
                        .param("startTime", "")
                        .param("endTime", "")
                        .param("durationMinutes", "")
                        .param("sessionType", "")
                        .param("notes", "My planned notes")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("studySessionDto",
                        "subjectId", "sessionDate", "sessionType", "durationMinutes"))
                .andExpect(model().attribute("showSessionModal", true))
                .andExpect(content().string(containsString("session-modal-overlay open")))
                .andExpect(content().string(containsString("Study Sessions")))
                .andExpect(content().string(containsString("Subject is required.")))
                .andExpect(content().string(containsString("Session date is required.")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("My planned notes")));

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 13b — a non-numeric duration is a binding failure; list re-renders
    // with the modal open and nothing is saved.
    @Test
    void save_nonNumericDuration_reRendersListWithModalOpen() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of());

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "25")
                        .param("topicId", "")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "abc")
                        .param("sessionType", "FOCUS")
                        .param("notes", "Still here")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("studySessionDto", "durationMinutes"))
                .andExpect(model().attribute("showSessionModal", true))
                .andExpect(content().string(containsString("session-modal-overlay open")))
                .andExpect(content().string(containsString("Study Sessions")))
                .andExpect(content().string(containsString("Still here")));

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 14 — the enum stays strongly typed and persists by NAME.
    @Test
    void save_persistsTypedEnumByName() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/study-sessions/save")
                        .param("subjectId", "25")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "90")
                        .param("sessionType", "REVISION")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study-sessions"));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());
        // Java enum stays typed; @Enumerated(STRING) persists "REVISION" (never an ordinal).
        assertThat(captor.getValue().getSessionType()).isEqualTo(StudySessionType.REVISION);
        assertThat(captor.getValue().getSessionType().name()).isEqualTo("REVISION");
    }

    // ================================================================
    // Sub-Phase E — Edit / Update / Delete / Session History
    // ================================================================

    // TEST 15 — the edit page opens pre-populated from the user-owned session.
    @Test
    void getEdit_sessionOwnedByUser_rendersPrefilledForm() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Topic topicA = ownedTopic(1000L, "Spring Security", subjectA);
        stubOwnSubjects(a, List.of(subjectA));
        when(topicRepository.findBySubject_UserOrderByIdAsc(a))
                .thenReturn(List.of(topicA));
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(session(50L, subjectA, topicA,
                        LocalDate.of(2026, 8, 29), LocalTime.of(9, 0), LocalTime.of(10, 0), 60,
                        StudySessionType.FOCUS, "Reviewed auth flow")));

        MvcResult result = mockMvc.perform(get("/study-sessions/edit/50")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("studySessionDto", "subjects", "topicsBySubject"))
                .andExpect(content().string(containsString("Edit Study Session")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("Spring Security")))
                .andExpect(content().string(containsString("value=\"2026-08-29\"")))
                .andExpect(content().string(containsString("name=\"startTime\" value=\"09:00")))
                .andExpect(content().string(containsString("Reviewed auth flow")))
                .andExpect(content().string(containsString("Update Study Session")))
                .andReturn();

        // The form is pre-populated from the user-owned session (Subject, Topic,
        // date, time, duration, type, notes).
        StudySessionDto dto = (StudySessionDto) result.getModelAndView().getModel().get("studySessionDto");
        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.subjectId()).isEqualTo(25L);
        assertThat(dto.topicId()).isEqualTo(1000L);
        assertThat(dto.subjectName()).isEqualTo("Spring Boot");
        assertThat(dto.topicName()).isEqualTo("Spring Security");
        assertThat(dto.sessionDate()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(dto.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(dto.endTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(dto.durationMinutes()).isEqualTo(60);
        assertThat(dto.sessionType()).isEqualTo(StudySessionType.FOCUS);
        assertThat(dto.notes()).isEqualTo("Reviewed auth flow");

        // The session was resolved through the user-scoped lookup only.
        verify(studySessionRepository).findByIdAndSubject_User(eq(50L), any());
    }

    // TEST 16 — another user's session on the edit page is forbidden.
    @Test
    void getEdit_anotherUsersSession_isForbiddenWithoutExposingData() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        // Session 50 belongs to User B: the user-scoped lookup returns nothing.
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/study-sessions/edit/50")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // TEST 17 — a valid update persists every changed field via P/R/G.
    @Test
    void updateSession_validData_updatesAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        Subject calculus = ownedSubject(26L, "Calculus", a);
        StudySession existing = session(50L, subjectA, null,
                LocalDate.of(2026, 8, 29), LocalTime.of(9, 0), LocalTime.of(10, 0), 60,
                StudySessionType.FOCUS, "Old notes");
        stubOwnSubjects(a, List.of(subjectA, calculus));
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(existing));
        when(subjectRepository.findByIdAndUser(eq(26L), any())).thenReturn(Optional.of(calculus));
        when(studySessionRepository.save(any(StudySession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "26")
                        .param("topicId", "")
                        .param("sessionType", "PRACTICE")
                        .param("sessionDate", "2026-11-20")
                        .param("startTime", "09:30")
                        .param("endTime", "11:30")
                        .param("durationMinutes", "120")
                        .param("notes", "Updated notes")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study-sessions"))
                .andExpect(flash().attribute("successMessage", "Study session updated successfully!"));

        ArgumentCaptor<StudySession> captor = ArgumentCaptor.forClass(StudySession.class);
        verify(studySessionRepository).save(captor.capture());

        // The existing entity is mutated and persisted (same row, moved subject).
        StudySession updated = captor.getValue();
        assertThat(updated).isSameAs(existing);
        assertThat(updated.getSubject()).isSameAs(calculus);
        assertThat(updated.getTopic()).isNull();
        assertThat(updated.getSessionType()).isEqualTo(StudySessionType.PRACTICE);
        assertThat(updated.getSessionDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(updated.getDurationMinutes()).isEqualTo(120);
        assertThat(updated.getNotes()).isEqualTo("Updated notes");
    }

    // TEST 18 — invalid edit data re-renders the form WITHOUT saving (no redirect).
    @Test
    void updateSession_invalidData_returnsEditFormWithoutSaving() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "")
                        .param("topicId", "")
                        .param("sessionType", "")
                        .param("sessionDate", "")
                        .param("durationMinutes", "")
                        .param("notes", "Edited notes")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("studySessionDto",
                        "subjectId", "sessionDate", "sessionType", "durationMinutes"))
                .andExpect(content().string(containsString("Edit Study Session")))
                .andExpect(content().string(containsString("Subject is required.")))
                .andExpect(content().string(containsString("Session date is required.")))
                .andExpect(content().string(containsString("Spring Boot")))
                .andExpect(content().string(containsString("Edited notes")));

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 19 — another user's session cannot be updated.
    @Test
    void updateSession_anotherUsersSession_isForbidden() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "25")
                        .param("sessionType", "FOCUS")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 20 — moving a session to another user's subject is rejected.
    @Test
    void updateSession_maliciousSubjectId_isRejected() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(session(50L, subjectA, null,
                        LocalDate.of(2026, 8, 29), null, null, 60, StudySessionType.FOCUS, null)));
        // Subject 30 belongs to another user: never resolves for User A.
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "30")
                        .param("sessionType", "FOCUS")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 21 — a topic from a DIFFERENT subject during edit is rejected.
    @Test
    void updateSession_topicFromAnotherSubject_isRejected() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(session(50L, subjectA, null,
                        LocalDate.of(2026, 8, 29), null, null, 60, StudySessionType.FOCUS, null)));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subjectA));
        // Topic 1000 belongs to another (or a different) subject: never matches subject 25.
        when(topicRepository.findByIdAndSubject(eq(1000L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "25")
                        .param("topicId", "1000")
                        .param("sessionType", "FOCUS")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "60")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 22 — a non-numeric duration during edit is a binding failure; the
    // edit form re-renders with submitted values preserved and nothing saved.
    @Test
    void updateSession_nonNumericDuration_reRendersEditForm() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));

        mockMvc.perform(post("/study-sessions/update/50")
                        .param("subjectId", "25")
                        .param("sessionType", "FOCUS")
                        .param("sessionDate", "2026-08-29")
                        .param("durationMinutes", "abc")
                        .param("notes", "Still here")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("studySessionDto", "durationMinutes"))
                .andExpect(content().string(containsString("Edit Study Session")))
                .andExpect(content().string(containsString("Still here")));

        verify(studySessionRepository, never()).save(any());
    }

    // TEST 23 — an owned session is deleted via P/R/G with success feedback.
    @Test
    void deleteSession_ownsSession_deletesAndRedirects() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        StudySession existing = session(50L, subjectA, null,
                LocalDate.of(2026, 8, 29), null, null, 60, StudySessionType.FOCUS, null);
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/study-sessions/delete/50")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study-sessions"))
                .andExpect(flash().attribute("successMessage", "Study session deleted successfully!"));

        verify(studySessionRepository).delete(existing);
    }

    // TEST 24 — another user's session is never deleted.
    @Test
    void deleteSession_anotherUsersSession_isForbiddenAndNotDeleted() throws Exception {
        User a = userA();
        stubOwnSubjects(a, List.of());
        when(studySessionRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/study-sessions/delete/50")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(studySessionRepository, never()).delete(any());
    }

    // TEST 25 — edit/update/delete routes are authenticated (anonymous → login).
    @Test
    void editUpdateAndDeleteRoutes_anonymous_redirectToLogin() throws Exception {
        mockMvc.perform(get("/study-sessions/edit/50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/study-sessions/update/50").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/study-sessions/delete/50").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // TEST 26 — session history renders newest session first (date, then time,
    // descending) and groups the records by month.
    @Test
    void getList_history_newestSessionsFirst() throws Exception {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "Spring Boot", a);
        stubOwnSubjects(a, List.of(subjectA));
        // Repository (newest-first) returns: Sep 1, then Aug 30 (09:00), then Aug 29.
        when(studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(a))
                .thenReturn(List.of(
                        session(3L, subjectA, null, LocalDate.of(2026, 9, 1),
                                LocalTime.of(18, 0), LocalTime.of(19, 0), 60,
                                StudySessionType.REVISION, "Sep session"),
                        session(2L, subjectA, null, LocalDate.of(2026, 8, 30),
                                LocalTime.of(9, 0), null, 60,
                                StudySessionType.FOCUS, "Aug 30 notes"),
                        session(1L, subjectA, null, LocalDate.of(2026, 8, 29),
                                LocalTime.of(9, 0), null, 60,
                                StudySessionType.FOCUS, "Aug 29 notes")));

        MvcResult result = mockMvc.perform(get("/study-sessions")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("sessionGroups", "today"))
                .andExpect(content().string(containsString("September 2026")))
                .andExpect(content().string(containsString("August 2026")))
                .andExpect(content().string(containsString("1 session")))
                .andExpect(content().string(containsString("2 sessions")))
                .andReturn();

        // Newest session is rendered before older ones.
        String body = result.getResponse().getContentAsString();
        assertThat(body.indexOf("Sep session")).isLessThan(body.indexOf("Aug 30 notes"));
        assertThat(body.indexOf("Sep session")).isLessThan(body.indexOf("Aug 29 notes"));
        assertThat(body.indexOf("Aug 30 notes")).isLessThan(body.indexOf("Aug 29 notes"));
    }
}