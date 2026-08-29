package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.DashboardData;
import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.dto.SetupStep;
import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.dto.UpcomingExam;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.DashboardService;
import com.studyflow.studyflow.service.ExamService;
import com.studyflow.studyflow.service.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DashboardService}.
 *
 * Produces the user dashboard from real application state only:
 *
 * - the personalized greeting and date are derived from the server clock
 * - the displayed name comes from the authenticated user's record
 * - the setup checklist is computed from data that actually exists
 *   (account creation always counts, subjects come from the subject module)
 * - the upcoming exams section shows the user's real nearest exams via the
 *   ownership-aware {@link ExamService}
 * - steps that depend on not-yet-implemented modules (topics, tasks) are
 *   marked as future rather than fabricated
 * - the subject snapshot shows ONLY the authenticated user's subjects via
 *   the ownership-aware {@link SubjectService}
 *
 * Subjects and exams are user-owned: the underlying services resolve the
 * authenticated user from the security context email and return only that
 * user's data, so one user can never see another user's exams here.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);

    static final int DASHBOARD_EXAM_LIMIT = 3;
    private static final int SETUP_TOTAL = 5;

    private final UserRepository userRepository;
    private final SubjectService subjectService;
    private final ExamService examService;

    /**
     * Constructor injection of the repositories and existing services.
     */
    public DashboardServiceImpl(UserRepository userRepository,
                                SubjectService subjectService,
                                ExamService examService) {
        this.userRepository = userRepository;
        this.subjectService = subjectService;
        this.examService = examService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardData getDashboardData(String userEmail) {

        String userName = resolveUserName(userEmail);
        String displayName = firstToken(userName);

        LocalDate today = LocalDate.now();

        // Only the authenticated user's subjects are shown on the dashboard.
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);
        boolean hasSubjects = !subjects.isEmpty();

        // Only the authenticated user's exams: the db-side "upcoming" query is
        // scoped to the owning user and returns today-or-future exams soonest
        // first, so the nearest exam is already the first element.
        List<UpcomingExam> upcomingExams = examService
                .getExams(userEmail, null, null, null, true)
                .stream()
                .limit(DASHBOARD_EXAM_LIMIT)
                .map(this::toUpcomingExam)
                .collect(Collectors.toList());

        boolean hasExams = examService.userHasAnyExam(userEmail);
        boolean hasUpcomingExams = !upcomingExams.isEmpty();

        List<SetupStep> setupSteps = buildSetupSteps(hasSubjects, hasExams);
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
                upcomingExams,
                hasUpcomingExams,
                upcomingExams.size(),
                upcomingExams.isEmpty() ? null : upcomingExams.get(0),
                setupSteps,
                setupCompleted,
                SETUP_TOTAL,
                Math.round(setupCompleted * 100f / SETUP_TOTAL)
        );
    }

    /**
     * Wrap a user-scoped exam DTO into its dashboard view, computing the
     * urgency from the real exam date against the server clock.
     */
    private UpcomingExam toUpcomingExam(ExamDto exam) {
        LocalDate today = LocalDate.now();
        long daysLeft = ChronoUnit.DAYS.between(today, exam.examDate());
        boolean isToday = exam.examDate().equals(today);
        return new UpcomingExam(
                exam.id(),
                exam.name(),
                exam.subjectName(),
                exam.examType(),
                exam.examDate(),
                exam.examTime(),
                exam.durationMinutes(),
                daysLeft,
                isToday,
                urgencyLabel(daysLeft, isToday)
        );
    }

    /**
     * Human urgency label derived from the actual date gap.
     */
    private String urgencyLabel(long daysLeft, boolean isToday) {
        if (isToday || daysLeft <= 0) {
            return "Today";
        }
        if (daysLeft == 1) {
            return "Tomorrow";
        }
        if (daysLeft < 7) {
            return daysLeft + " days";
        }
        long weeks = daysLeft / 7;
        return weeks == 1 ? "1 week" : weeks + " weeks";
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
     * The subject and exam steps reflect real data: the exam step only
     * becomes the next actionable step once the user owns at least one
     * subject (an exam cannot be created without one). Topics and tasks
     * belong to modules that do not exist yet, so they are marked UPCOMING.
     */
    private List<SetupStep> buildSetupSteps(boolean hasSubjects, boolean hasExams) {
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
                hasExams ? SetupStep.Status.COMPLETED
                        : hasSubjects ? SetupStep.Status.CURRENT
                        : SetupStep.Status.UPCOMING,
                hasExams || !hasSubjects ? null : "/exams"
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
