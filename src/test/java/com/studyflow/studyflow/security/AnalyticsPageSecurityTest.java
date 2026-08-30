package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sub-Phase I page tests: the /analytics route is behind authentication,
 * renders the authenticated user's REAL aggregated statistics, and shows an
 * honest empty state (not fabricated numbers) before any study activity.
 *
 * The aggregation repository is stubbed here; the SQL behind it is verified
 * against the live database in {@link AnalyticsQueryIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsPageSecurityTest {

    private static final String EMAIL = "analytics-student@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private StudySessionRepository studySessionRepository;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .name("Analytics Student")
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

    private void stubNoStudyData() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser()));

        when(studySessionRepository.sumDurationMinutesBySubject_User(any())).thenReturn(0L);
        when(studySessionRepository.sumDurationMinutesBySubject_UserAndSessionDateBetween(
                any(), any(), any())).thenReturn(0L);
        when(studySessionRepository.countBySubject_User(any())).thenReturn(0L);
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(any()))
                .thenReturn(List.of());
        when(studySessionRepository.sumDurationMinutesByDateForUser(any(), any(), any()))
                .thenReturn(List.of());
        when(studySessionRepository.sumDurationMinutesBySubjectForUser(any()))
                .thenReturn(new ArrayList<>());
        when(studySessionRepository.sumDurationMinutesByTypeForUser(any()))
                .thenReturn(new ArrayList<>());
        when(studySessionRepository.findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(any()))
                .thenReturn(List.of());
    }

    @Test
    void analytics_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/analytics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void analytics_withNoStudyData_showsHonestEmptyState() throws Exception {
        stubNoStudyData();

        mockMvc.perform(get("/analytics")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                // Real overview with honest zeroes.
                .andExpect(content().string(containsString("Analytics")))
                .andExpect(content().string(containsString("Total Study Time")))
                .andExpect(content().string(containsString("0m")))
                .andExpect(content().string(containsString("Total Sessions")))
                .andExpect(content().string(containsString("Current Streak")))
                // Business-neutral, motivational empty state with a real action.
                .andExpect(content().string(containsString("No study activity yet.")))
                .andExpect(content().string(containsString("Start your first study session to see your progress here.")))
                .andExpect(content().string(containsString("Start a Study Session")))
                .andExpect(content().string(containsString("/study-sessions")))
                // Charts and ranked lists are hidden until real data exists.
                .andExpect(content().string(not(containsString("Daily Study Trend"))))
                .andExpect(content().string(not(containsString("Subject Distribution"))))
                .andExpect(content().string(not(containsString("Session Types"))))
                .andExpect(content().string(not(containsString("Recent Study Activity"))));
    }

    @Test
    void analytics_withRealStudyData_rendersRealStatistics() throws Exception {
        LocalDate today = LocalDate.now();

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buildUser()));

        // 200m all time, 125m this week, 300m this month, 6 sessions.
        when(studySessionRepository.sumDurationMinutesBySubject_User(any())).thenReturn(200L);
        when(studySessionRepository.sumDurationMinutesBySubject_UserAndSessionDateBetween(
                any(), any(), any())).thenReturn(125L, 300L);
        when(studySessionRepository.countBySubject_User(any())).thenReturn(6L);

        // Active today and yesterday.
        when(studySessionRepository.findDistinctActiveDatesBySubject_User(any()))
                .thenReturn(List.of(today, today.minusDays(1)));

        List<Object[]> subjectRows = new ArrayList<>();
        subjectRows.add(new Object[]{1L, "Database", "Purple", 150L});
        when(studySessionRepository.sumDurationMinutesBySubjectForUser(any()))
                .thenReturn(subjectRows);

        List<Object[]> typeRows = new ArrayList<>();
        typeRows.add(new Object[]{StudySessionType.FOCUS, 180L});
        typeRows.add(new Object[]{StudySessionType.REVISION, 60L});
        when(studySessionRepository.sumDurationMinutesByTypeForUser(any()))
                .thenReturn(typeRows);

        StudySession recent = new StudySession();
        recent.setSubject(buildSubject());
        recent.setSessionDate(today);
        recent.setSessionType(StudySessionType.FOCUS);
        recent.setDurationMinutes(60);
        when(studySessionRepository.findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(any()))
                .thenReturn(List.of(recent));

        mockMvc.perform(get("/analytics")
                        .with(user(EMAIL).roles("USER")))
                .andExpect(status().isOk())
                // Real, formatted totals throughout.
                .andExpect(content().string(containsString("3h 20m")))
                .andExpect(content().string(containsString("2h 5m")))
                .andExpect(content().string(containsString("5h")))
                .andExpect(content().string(containsString("6")))
                // Streaks from the active dates.
                .andExpect(content().string(containsString("Current Streak")))
                .andExpect(content().string(containsString("Longest Streak")))
                // Charts and breakdowns appear once real data exists.
                .andExpect(content().string(containsString("Daily Study Trend")))
                .andExpect(content().string(containsString("Subject Distribution")))
                .andExpect(content().string(containsString("Session Types")))
                .andExpect(content().string(containsString("Recent Study Activity")))
                .andExpect(content().string(containsString("/js/analytics.js")))
                // Ranked lists render real subject and type values.
                .andExpect(content().string(containsString("Database")))
                .andExpect(content().string(containsString("2h 30m")))
                .andExpect(content().string(containsString("Focus")))
                .andExpect(content().string(containsString("3h")))
                // The empty state is gone.
                .andExpect(content().string(not(containsString("No study activity yet."))));
    }
}