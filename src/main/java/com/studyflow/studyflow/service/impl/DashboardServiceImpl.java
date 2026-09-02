package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.DashboardData;
import com.studyflow.studyflow.dto.DashboardSubjectTopics;
import com.studyflow.studyflow.dto.DashboardTaskItem;
import com.studyflow.studyflow.dto.DayStudyMinutes;
import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.dto.RecentStudySession;
import com.studyflow.studyflow.dto.SetupStep;
import com.studyflow.studyflow.dto.StudyProgress;
import com.studyflow.studyflow.dto.StudyStreak;
import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.dto.SubjectStudySummary;
import com.studyflow.studyflow.dto.UpcomingExam;
import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.DashboardService;
import com.studyflow.studyflow.service.ExamService;
import com.studyflow.studyflow.service.SubjectService;
import com.studyflow.studyflow.service.UserSettingsService;
import com.studyflow.studyflow.util.StudyTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DashboardService}.
 *
 * Produces the user dashboard from real application state only:
 *
 * - the personalized greeting and date are derived from the server clock
 * - the displayed name comes from the authenticated user's record
 * - the summary cards (subjects, today's tasks, upcoming exams, study
 *   streak) carry only real, user-scoped numbers — never invented values
 * - the setup checklist is computed from data that actually exists
 *   (account creation always counts, subjects/exams/topics/tasks reflect real data)
 * - the upcoming exams section shows the user's real nearest exams via the
 *   ownership-aware {@link ExamService}, and the exam summary total comes
 *   from a user-scoped count query
 * - topic previews and counts are aggregated through user-scoped queries,
 *   and each subject card carries its real task-completion snapshot
 * - today's, overdue, due-tomorrow and upcoming tasks come from user-scoped
 *   task queries; today's progress is derived from the real due-today set
 * - the study streak is computed from the user's real active study dates
 *   (single distinct-date query, never in-memory filtering of all sessions)
 * - the subject snapshot shows ONLY the authenticated user's subjects via
 *   the ownership-aware {@link SubjectService}
 * - the Study Progress and Recent Study Sessions sections are aggregated
 *   from the authenticated user's real sessions by user-scoped database
 *   queries (sums, counts and groups; never in-memory filtering)
 *
 * Subjects, topics and tasks are user-owned: the underlying repositories
 * resolve the owning {@link User} and return only that user's data, so
 * one user can never see another user's dashboard state.
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);

    static final int DASHBOARD_EXAM_LIMIT = 3;
    private static final int DASHBOARD_TOPIC_PREVIEW_LIMIT = 3;
    private static final int DASHBOARD_TASK_LIMIT = 5;
    private static final int SETUP_TOTAL = 5;
    private static final int DAYS_IN_WEEK = 7;

    private final UserRepository userRepository;
    private final SubjectService subjectService;
    private final ExamService examService;
    private final ExamRepository examRepository;
    private final StudySessionRepository studySessionRepository;
    private final TopicRepository topicRepository;
    private final TaskRepository taskRepository;
    private final UserSettingsService userSettingsService;

    /**
     * Constructor injection of the repositories and existing services.
     */
    public DashboardServiceImpl(UserRepository userRepository,
                                SubjectService subjectService,
                                ExamService examService,
                                ExamRepository examRepository,
                                StudySessionRepository studySessionRepository,
                                TopicRepository topicRepository,
                                TaskRepository taskRepository,
                                UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.subjectService = subjectService;
        this.examService = examService;
        this.examRepository = examRepository;
        this.studySessionRepository = studySessionRepository;
        this.topicRepository = topicRepository;
        this.taskRepository = taskRepository;
        this.userSettingsService = userSettingsService;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardData getDashboardData(String userEmail) {

        String userName = resolveUserName(userEmail);
        String displayName = firstToken(userName);

        LocalDate today = LocalDate.now();
        User user = resolveUser(userEmail);

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

        // Topics per subject: one grouped query for counts + all user topics
        // for previews, bounded by the subject list size.
        List<DashboardSubjectTopics> subjectTopics =
                buildSubjectTopics(subjects, user);
        boolean hasSubjectTopics = subjectTopics.stream()
                .anyMatch(DashboardSubjectTopics::hasTopics);

        // Today's tasks: the user-scoped "due today" query returns the full
        // set, so the true totals are derived from real rows BEFORE the
        // display cap is applied. Progress is computed from these real counts.
        List<Task> dueToday = user == null ? List.of()
                : taskRepository.findDueTodayByUser(user, today);
        int todayTaskCount = dueToday.size();
        int completedTodayCount = (int) dueToday.stream()
                .filter(t -> Boolean.TRUE.equals(t.getCompleted()))
                .count();
        int todayCompletedPercent = todayTaskCount > 0
                ? (int) Math.round(completedTodayCount * 100.0 / todayTaskCount)
                : 0;
        List<DashboardTaskItem> todayTasks = dueToday.stream()
                .limit(DASHBOARD_TASK_LIMIT)
                .map(t -> DashboardTaskItem.fromEntity(t, today))
                .collect(Collectors.toList());

        // Upcoming tasks (after today): user-scoped, limited, incomplete-first.
        List<DashboardTaskItem> upcomingTasks =
                buildUpcomingTasks(user, today);

        // Attention area: incomplete overdue tasks and tasks due tomorrow.
        List<DashboardTaskItem> overdueTasks = List.of();
        long overdueCount = 0;
        List<DashboardTaskItem> tomorrowTasks = List.of();
        if (user != null) {
            overdueCount = taskRepository.countBySubject_UserAndDueDateBeforeAndCompleted(user, today, Boolean.FALSE);
            overdueTasks = taskRepository
                    .findTop3BySubject_UserAndDueDateBeforeAndCompletedFalseOrderByDueDateAscDueTimeAscIdAsc(user, today)
                    .stream()
                    .map(t -> DashboardTaskItem.fromEntity(t, today))
                    .collect(Collectors.toList());
            tomorrowTasks = taskRepository
                    .findTop3BySubject_UserAndDueDateAndCompletedFalseOrderByDueTimeAscIdAsc(user, today.plusDays(1))
                    .stream()
                    .map(t -> DashboardTaskItem.fromEntity(t, today))
                    .collect(Collectors.toList());
        }
        boolean hasOverdueTasks = overdueCount > 0;
        boolean hasTomorrowTasks = !tomorrowTasks.isEmpty();

        // Study streak derived from the user's real active study dates.
        StudyStreak studyStreak = buildStudyStreak(user);

        List<SetupStep> setupSteps = buildSetupSteps(
                hasSubjects, hasExams, hasSubjectTopics, !todayTasks.isEmpty());
        int setupCompleted = (int) setupSteps.stream()
                .filter(step -> step.status() == SetupStep.Status.COMPLETED)
                .count();

        // Study sessions are aggregated per owning user via the subject
        // chain, so only the authenticated user's sessions ever count. The
        // weekly goal comes from the user's own settings (single source of
        // truth; defaults when the user has no settings row yet).
        int weeklyGoalMinutes = UserSettingsDto.defaults().weeklyStudyGoalMinutes();
        if (user != null) {
            weeklyGoalMinutes = userSettingsService
                    .getSettingsForUser(user.getEmail())
                    .weeklyStudyGoalMinutes();
        }

        StudyProgress studyProgress = buildStudyProgress(user, today, weeklyGoalMinutes);

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
                subjectTopics,
                hasSubjectTopics,
                todayTasks,
                !todayTasks.isEmpty(),
                todayTaskCount,
                completedTodayCount,
                todayCompletedPercent,
                overdueCount,
                overdueTasks,
                hasOverdueTasks,
                tomorrowTasks,
                hasTomorrowTasks,
                tomorrowTasks.size(),
                upcomingTasks,
                !upcomingTasks.isEmpty(),
                upcomingTasks.size(),
                upcomingExams,
                hasUpcomingExams,
                upcomingExams.size(),
                upcomingExamTotal(user, today),
                upcomingExams.isEmpty() ? null : upcomingExams.get(0),
                studyStreak,
                setupSteps,
                setupCompleted,
                SETUP_TOTAL,
                Math.round(setupCompleted * 100f / SETUP_TOTAL),
                studyProgress
        );
    }

    /**
     * Build the user's real study statistics for the Study Progress section.
     *
     * All sums, counts and groups are computed by user-scoped database
     * queries against the owning {@link User} (via the subject chain); no
     * sessions are loaded into memory and re-filtered here. The week is
     * defined from the start of the current week through today (Monday
     * rule), so "today" is always part of "this week".
     *
     * A missing account simply yields an empty progress object: the page
     * renders honest "0m" labels instead of fabricated numbers.
     *
     * @param weeklyGoalMinutes the user's weekly goal from their settings
     *                          (single source of truth); the progress
     *                          percentage is derived from real week minutes,
     *                          never invented
     */
    private StudyProgress buildStudyProgress(User user,
                                             LocalDate today,
                                             int weeklyGoalMinutes) {
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        Map<LocalDate, Long> minutesByDate = new HashMap<>();
        long todayMinutes = 0;
        long weekMinutes = 0;
        long weekSessionCount = 0;
        long totalMinutes = 0;
        List<RecentStudySession> recentSessions = new ArrayList<>();
        List<SubjectStudySummary> subjectSummaries = new ArrayList<>();

        if (user != null) {
            weekMinutes = nullSafeLong(studySessionRepository
                    .sumDurationMinutesBySubject_UserAndSessionDateBetween(user, weekStart, today));
            weekSessionCount = studySessionRepository
                    .countBySubject_UserAndSessionDateBetween(user, weekStart, today);

            for (Object[] row : studySessionRepository
                    .sumDurationMinutesByDateForUser(user, weekStart, today)) {
                LocalDate date = (LocalDate) row[0];
                minutesByDate.put(date, ((Number) row[1]).longValue());
            }
            todayMinutes = minutesByDate.getOrDefault(today, 0L);

            recentSessions = studySessionRepository
                    .findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(user)
                    .stream()
                    .map(this::toRecentStudySession)
                    .collect(Collectors.toList());

            List<Object[]> bySubject =
                    studySessionRepository.sumDurationMinutesBySubjectForUser(user);
            bySubject.sort(Comparator.comparingLong(
                    row -> -((Number) row[3]).longValue()));
            for (Object[] row : bySubject) {
                subjectSummaries.add(new SubjectStudySummary(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        StudyTimeFormatter.formatMinutes(((Number) row[3]).longValue())
                ));
            }

            // All-time study time: one user-scoped aggregation.
            totalMinutes = nullSafeLong(
                    studySessionRepository.sumDurationMinutesBySubject_User(user));
        }

        List<DayStudyMinutes> dailyMinutes =
                buildDailyMinutes(weekStart, today, minutesByDate);

        boolean hasRecentSessions = !recentSessions.isEmpty();
        boolean hasSubjectSummaries = !subjectSummaries.isEmpty();
        boolean hasStudy = hasSubjectSummaries || weekSessionCount > 0;
        boolean hasTotal = totalMinutes > 0;

        // Honest goal progress: the percentage is the real week's minutes
        // over the user's own goal. A goal is always positive (validated on
        // save; the default is 300), so a zero/negative goal renders 0%
        // instead of dividing by zero.
        int weeklyGoalPercent = weeklyGoalMinutes > 0
                ? (int) Math.round(weekMinutes * 100.0 / weeklyGoalMinutes)
                : 0;

        return new StudyProgress(
                StudyTimeFormatter.formatMinutes(todayMinutes),
                StudyTimeFormatter.formatMinutes(weekMinutes),
                weekSessionCount,
                hasStudy,
                dailyMinutes,
                recentSessions,
                hasRecentSessions,
                subjectSummaries,
                hasSubjectSummaries,
                StudyTimeFormatter.formatMinutes(weeklyGoalMinutes),
                weeklyGoalPercent,
                StudyTimeFormatter.formatMinutes(totalMinutes),
                hasTotal
        );
    }

    /**
     * Build the seven-day (Monday-Sunday) breakdown of the current week.
     * Days outside the range carry 0 minutes; the bar percentage is
     * relative to the week's largest day so the chart is self-scaling.
     */
    private List<DayStudyMinutes> buildDailyMinutes(LocalDate weekStart,
                                                    LocalDate today,
                                                    Map<LocalDate, Long> minutesByDate) {
        long weekMax = 0;
        long[] minutes = new long[DAYS_IN_WEEK];
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            LocalDate day = weekStart.plusDays(i);
            minutes[i] = minutesByDate.getOrDefault(day, 0L);
            weekMax = Math.max(weekMax, minutes[i]);
        }

        List<DayStudyMinutes> days = new ArrayList<>(DAYS_IN_WEEK);
        for (int i = 0; i < DAYS_IN_WEEK; i++) {
            LocalDate day = weekStart.plusDays(i);
            boolean isToday = day.equals(today);
            int barPercent = weekMax == 0 ? 0
                    : (int) Math.round(minutes[i] * 100.0 / weekMax);
            days.add(new DayStudyMinutes(
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    (int) minutes[i],
                    StudyTimeFormatter.formatMinutes(minutes[i]),
                    barPercent,
                    isToday
            ));
        }
        return days;
    }

    /**
     * Convert a user-scoped study session into its dashboard view.
     *
     * Called inside the read-only transaction, so the lazy subject/topic
     * relationships are resolved before the mapping returns. Ownership was
     * already guaranteed by the {@code findTop5BySubject_User...} query.
     */
    private RecentStudySession toRecentStudySession(StudySession session) {
        Subject subject = session.getSubject();
        Topic topic = session.getTopic();
        return new RecentStudySession(
                session.getId(),
                subject != null ? subject.getName() : null,
                subject != null ? subject.getColor() : null,
                topic != null ? topic.getName() : null,
                session.getSessionDate(),
                StudyTimeFormatter.formatMinutes(
                        session.getDurationMinutes() == null ? 0 : session.getDurationMinutes()),
                sessionTypeLabel(session.getSessionType())
        );
    }

    /**
     * Human label for a session type, e.g. EXAM_PREPARATION → "Exam Preparation".
     */
    private String sessionTypeLabel(StudySessionType type) {
        if (type == null) {
            return "Session";
        }
        return Arrays.stream(type.name().toLowerCase(Locale.ENGLISH).split("_"))
                .map(word -> word.isEmpty() ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * Wrap a user-scoped exam DTO into its dashboard view, computing the
     * urgency from the real exam date against the server clock.
     */
    private UpcomingExam toUpcomingExam(ExamDto exam) {
        LocalDate today = LocalDate.now();
        long daysLeft = ChronoUnit.DAYS.between(today, exam.examDate());
        boolean isToday = exam.examDate().equals(today);
        boolean isTomorrow = exam.examDate().equals(today.plusDays(1));
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
                isTomorrow,
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
     * Resolve the authenticated user from the security context email,
     * or null when the record cannot be found.
     */
    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
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
        User user = resolveUser(userEmail);
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
     * The subject, topic, task and exam steps reflect real data.
     */
    private List<SetupStep> buildSetupSteps(boolean hasSubjects,
                                            boolean hasExams,
                                            boolean hasTopics,
                                            boolean hasTasks) {
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
                hasTopics ? SetupStep.Status.COMPLETED
                        : hasSubjects ? SetupStep.Status.CURRENT
                        : SetupStep.Status.UPCOMING,
                hasTopics || !hasSubjects ? null : "/subjects"
        ));

        steps.add(new SetupStep(
                4,
                "Create your first task",
                "bi-list-check",
                hasTasks ? SetupStep.Status.COMPLETED
                        : hasSubjects ? SetupStep.Status.CURRENT
                        : SetupStep.Status.UPCOMING,
                hasTasks || !hasSubjects ? null : "/subjects"
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
     * Build per-subject topic summaries for the dashboard.
     *
     * One grouped query produces subject-scoped counts; a single
     * user-scoped fetch provides previews. Both are bounded to the
     * number of displayed subjects and the preview limit — no N+1.
     */
    private List<DashboardSubjectTopics> buildSubjectTopics(
            List<SubjectDto> subjects, User user) {

        Map<Long, Long> countBySubjectId = new HashMap<>();
        Map<Long, List<String>> previewBySubjectId = new HashMap<>();
        Map<Long, Long> totalTasksBySubject = new HashMap<>();
        Map<Long, Long> doneTasksBySubject = new HashMap<>();

        if (user != null) {
            for (Object[] row : topicRepository.countTopicsPerSubjectForUser(user)) {
                Long subjectId = (Long) row[0];
                Long count     = (Long) row[2];
                countBySubjectId.put(subjectId, count);
            }
            List<Topic> allTopics =
                    topicRepository.findBySubject_UserOrderByIdAsc(user);
            for (Topic t : allTopics) {
                Long subjectId = t.getSubject().getId();
                previewBySubjectId
                        .computeIfAbsent(subjectId, k -> new ArrayList<>())
                        .add(t.getName());
            }
            // One grouped query gives every subject's real task completion.
            for (Object[] row : taskRepository.countTaskCompletionBySubjectForUser(user)) {
                Long subjectId = (Long) row[0];
                totalTasksBySubject.put(subjectId, (Long) row[1]);
                doneTasksBySubject.put(subjectId, (Long) row[2]);
            }
        }

        List<DashboardSubjectTopics> result = new ArrayList<>(subjects.size());
        for (SubjectDto s : subjects) {
            long count = countBySubjectId.getOrDefault(s.id(), 0L);
            List<String> allPreviews =
                    previewBySubjectId.getOrDefault(s.id(), List.of());
            List<String> limited = allPreviews.stream()
                    .limit(DASHBOARD_TOPIC_PREVIEW_LIMIT)
                    .collect(Collectors.toList());
            long taskCount = totalTasksBySubject.getOrDefault(s.id(), 0L);
            long doneCount = doneTasksBySubject.getOrDefault(s.id(), 0L);
            int taskPercent = taskCount > 0
                    ? (int) Math.round(doneCount * 100.0 / taskCount)
                    : 0;
            result.add(new DashboardSubjectTopics(
                    s.id(),
                    s.name(),
                    s.color(),
                    count,
                    count > 0,
                    limited,
                    allPreviews.size() > DASHBOARD_TOPIC_PREVIEW_LIMIT,
                    taskCount,
                    doneCount,
                    taskPercent,
                    taskCount > 0
            ));
        }
        return result;
    }

    /**
     * Build upcoming incomplete tasks (after today) for the dashboard,
     * soonest first, limited.
     */
    private List<DashboardTaskItem> buildUpcomingTasks(User user, LocalDate today) {
        if (user == null) return List.of();
        return taskRepository.findUpcomingIncompleteByUser(user, today)
                .stream()
                .limit(DASHBOARD_TASK_LIMIT)
                .map(t -> DashboardTaskItem.fromEntity(t, today))
                .collect(Collectors.toList());
    }

    /**
     * The user's current study streak in consecutive active days.
     *
     * Based on the real distinct dates on which the user recorded at least
     * one positive-duration session (a single aggregated query, scoped to the
     * owning user). Counting starts at today when today is already active,
     * otherwise at yesterday — a streak is never broken mid-day just because
     * the user has not studied yet. No streak is ever invented for an account
     * with no activity.
     */
    private StudyStreak buildStudyStreak(User user) {
        if (user == null) {
            return new StudyStreak(0, false, false);
        }
        Set<LocalDate> active = new HashSet<>(
                studySessionRepository.findDistinctActiveDatesBySubject_User(user));
        if (active.isEmpty()) {
            return new StudyStreak(0, false, false);
        }
        LocalDate today = LocalDate.now();
        boolean activeToday = active.contains(today);
        long days = 0;
        LocalDate cursor = activeToday ? today : today.minusDays(1);
        while (active.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return new StudyStreak(days, activeToday, true);
    }

    /**
     * The real total number of upcoming exams (today or later) owned by the
     * user. Aggregated in the database, so the summary card shows the honest
     * total even though the page only renders the nearest few.
     */
    private long upcomingExamTotal(User user, LocalDate today) {
        if (user == null) {
            return 0;
        }
        return examRepository.countBySubject_UserAndExamDateGreaterThanEqual(user, today);
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
