package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ExamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase H tests: the dashboard renders the user's REAL study statistics
 * (Study Progress + Recent Study Sessions), driven entirely by the
 * user-scoped aggregation queries, never by fabricated numbers.
 *
 * The aggregation repository is stubbed here; the SQL behind it is verified
 * separately against the real database in {@link DashboardStudyQueryIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardStudyProgressTest {

    private static final String EMAIL = "dash-study@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private StudySessionRepository studySessionRepository;

    @MockitoBean
    private ExamService examService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .name("Study Student")
                .email(EMAIL)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    private Subject buildSubject() {
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setName("Database");
        subject.setColor("Purple");
        return subject;
    }

    private StudySession session(Long id, StudySessionType type, LocalDate date,
                                 Integer duration, Topic topic) {
        StudySession session = new StudySession();
        session.setId(id);
        session.setSubject(buildSubject());
        session.setSessionType(type);
        session.setSessionDate(date);
        session.setDurationMinutes(duration);
        session.setTopic(topic);
        return session;
    }

    private void stubNoStudyData() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser()));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());
        when(examService.getExams(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of());
        when(examService.userHasAnyExam(any())).thenReturn(false);
    }

    @Test
    void dashboard_withNoStudySessions_showsHonestEmptyStates() throws Exception {
        stubNoStudyData();

        mockMvc.perform(get("/dashboard")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                // Study Progress card: empty state with a real action.
                .andExpect(content().string(containsString("Start your first study session")))
                .andExpect(content().string(containsString("Start a Study Session")))
                // No fabricated stat tiles.
                .andExpect(content().string(not(containsString("Today's Study Time"))))
                .andExpect(content().string(not(containsString("Sessions This Week"))))
                // Recent sessions card: empty state, no View All link.
                .andExpect(content().string(containsString("No recent study sessions yet.")))
                .andExpect(content().string(not(containsString("View All Sessions"))));
    }

    @Test
    void dashboard_withRealStudyData_rendersRealStatistics() throws Exception {
        LocalDate today = LocalDate.now();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser()));
        when(subjectRepository.findAllByUserOrderByIdAsc(any()))
                .thenReturn(List.of(buildSubject()));
        when(examService.getExams(any(), any(), any(), any(), anyBoolean())).thenReturn(List.of());
        when(examService.userHasAnyExam(any())).thenReturn(false);

        // Week total 155m, today 75m, 4 sessions this week.
        when(studySessionRepository.sumDurationMinutesBySubject_UserAndSessionDateBetween(
                any(), any(), any())).thenReturn(155L);
        when(studySessionRepository.countBySubject_UserAndSessionDateBetween(
                any(), any(), any())).thenReturn(4L);

        // Newest first: today's 45m revision with a topic, then today's 30m focus.
        Topic normalization = new Topic();
        normalization.setId(2L);
        normalization.setName("Normalization");
        when(studySessionRepository.findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(any()))
                .thenReturn(List.of(
                        session(2L, StudySessionType.REVISION, today, 45, normalization),
                        session(1L, StudySessionType.FOCUS, today, 30, null)
                ));

        List<Object[]> subjectRows = new ArrayList<>();
        subjectRows.add(new Object[]{1L, "Database", "Purple", 255L});
        when(studySessionRepository.sumDurationMinutesBySubjectForUser(any()))
                .thenReturn(subjectRows);

        List<Object[]> dateRows = new ArrayList<>();
        dateRows.add(new Object[]{today, 75L});
        dateRows.add(new Object[]{today.minusDays(1), 80L});
        when(studySessionRepository.sumDurationMinutesByDateForUser(any(), any(), any()))
                .thenReturn(dateRows);

        String expectedDate = today.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));

        mockMvc.perform(get("/dashboard")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                // Stat tiles carry the aggregated, formatted values.
                .andExpect(content().string(containsString("Today's Study Time")))
                .andExpect(content().string(containsString("1h 15m")))
                .andExpect(content().string(containsString("This Week")))
                .andExpect(content().string(containsString("2h 35m")))
                .andExpect(content().string(containsString("Sessions This Week")))
                .andExpect(content().string(containsString("4")))
                // Real recent sessions with topic, type and formatted duration.
                .andExpect(content().string(containsString("Recent Study Sessions")))
                .andExpect(content().string(containsString("Database")))
                .andExpect(content().string(containsString("Normalization")))
                .andExpect(content().string(containsString("Revision")))
                .andExpect(content().string(containsString("45m")))
                .andExpect(content().string(containsString(expectedDate)))
                // Study time by subject.
                .andExpect(content().string(containsString("Study time by subject")))
                .andExpect(content().string(containsString("4h 15m")))
                // Drill-through link to the sessions hub.
                .andExpect(content().string(containsString("View All Sessions")))
                .andExpect(content().string(containsString("/study-sessions")))
                // Empty states are gone once real data exists.
                .andExpect(content().string(not(containsString("No recent study sessions yet."))))
                // The weekly breakdown renders all seven weekday labels.
                .andExpect(content().string(containsString("Mon")))
                .andExpect(content().string(containsString("Sun")));
    }

    @Test
    void dashboard_quickStudyTools_linkToStudySessions() throws Exception {
        stubNoStudyData();

        mockMvc.perform(get("/dashboard")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                // Focus Timer and Study Sessions are no longer "Coming soon".
                .andExpect(content().string(containsString("Focus Timer")))
                .andExpect(content().string(containsString("Study Sessions")))
                .andExpect(content().string(containsString("/study-sessions")));
    }
}