package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.DashboardData;
import com.studyflow.studyflow.dto.SetupStep;
import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.DashboardService;
import com.studyflow.studyflow.service.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of {@link DashboardService}.
 *
 * Produces the user dashboard from real application state only:
 *
 * - the personalized greeting and date are derived from the server clock
 * - the displayed name comes from the authenticated user's record
 * - the setup checklist is computed from data that actually exists
 *   (account creation always counts, subjects come from the subject module)
 * - steps that depend on not-yet-implemented modules (topics, tasks, exams)
 *   are marked as future rather than fabricated
 * - the subject snapshot reuses the existing {@link SubjectService}
 *
 * Subject data is currently global because the Subject entity has no
 * relationship to User yet; a per-user relationship is a future-phase
 * requirement.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);

    private static final int SETUP_TOTAL = 5;
    private static final int MAX_SNAPSHOT_SUBJECTS = 6;

    private final UserRepository userRepository;
    private final SubjectService subjectService;

    /**
     * Constructor injection of the repositories and existing services.
     */
    public DashboardServiceImpl(UserRepository userRepository,
                                SubjectService subjectService) {
        this.userRepository = userRepository;
        this.subjectService = subjectService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardData getDashboardData(String userEmail) {

        String userName = resolveUserName(userEmail);
        String displayName = firstToken(userName);

        LocalDate today = LocalDate.now();

        List<SubjectDto> subjects = subjectService.getAllSubjects();
        boolean hasSubjects = !subjects.isEmpty();

        List<SetupStep> setupSteps = buildSetupSteps(hasSubjects);
        int setupCompleted = (int) setupSteps.stream()
                .filter(step -> step.status() == SetupStep.Status.COMPLETED)
                .count();

        return new DashboardData(
                userName,
                displayName,
                greetingForHour(LocalTime.now().getHour()),
                greetingMessageForHour(LocalTime.now().getHour()),
                today.format(DATE_LABEL),
                today.format(DAY_LABEL),
                subjects,
                hasSubjects,
                subjects.size(),
                setupSteps,
                setupCompleted,
                SETUP_TOTAL,
                Math.round(setupCompleted * 100f / SETUP_TOTAL)
        );
    }

    /**
     * Resolve the user's display name from the authenticated email.
     * Falls back to the email prefix when the record cannot be found,
     * so the dashboard never shows a hard-coded name.
     */
    private String resolveUserName(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return "";
        }
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        int at = userEmail.indexOf('@');
        return at > 0 ? userEmail.substring(0, at) : userEmail;
    }

    /**
     * Take the first word of a name so the greeting stays short.
     */
    private String firstToken(String name) {
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim().split("\\s+")[0];
    }

    /**
     * Build the five-step getting-started checklist.
     *
     * Account creation is always completed for an authenticated user.
     * The subject step reflects real data. Topics, tasks and exams belong
     * to modules that do not exist yet, so they are marked UPCOMING.
     */
    private List<SetupStep> buildSetupSteps(boolean hasSubjects) {
        List<SetupStep> steps = new ArrayList<>();

        steps.add(new SetupStep(
                1,
                "Create your account",
                "bi-person-check-fill",
                SetupStep.Status.COMPLETED,
                null
        ));

        steps.add(new SetupStep(
                2,
                "Add your first subject",
                "bi-book-half",
                hasSubjects ? SetupStep.Status.COMPLETED : SetupStep.Status.CURRENT,
                "/subjects/new"
        ));

        steps.add(new SetupStep(
                3,
                "Add your first topic",
                "bi-journal-text",
                SetupStep.Status.UPCOMING,
                null
        ));

        steps.add(new SetupStep(
                4,
                "Create your first task",
                "bi-list-check",
                SetupStep.Status.UPCOMING,
                null
        ));

        steps.add(new SetupStep(
                5,
                "Add your first exam",
                "bi-calendar-event",
                SetupStep.Status.UPCOMING,
                null
        ));

        return steps;
    }

    /**
     * Greeting text based on the current hour of day.
     */
    private String greetingForHour(int hour) {
        if (hour >= 5 && hour < 12) {
            return "Good morning";
        }
        if (hour >= 12 && hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    /**
     * Short supporting message that matches the time-of-day greeting.
     */
    private String greetingMessageForHour(int hour) {
        if (hour >= 5 && hour < 12) {
            return "Let's make today's study plan count.";
        }
        if (hour >= 12 && hour < 17) {
            return "Keep the momentum going — stay focused.";
        }
        return "Wrap up strong and set yourself up for tomorrow.";
    }
}
