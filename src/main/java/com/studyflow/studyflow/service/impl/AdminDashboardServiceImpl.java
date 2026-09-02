package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.AdminDashboardData;
import com.studyflow.studyflow.dto.AdminSetupCard;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.AdminDashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of AdminDashboardService.
 *
 * Aggregates the platform-level statistics for the admin dashboard using
 * only data that actually exists in the database:
 *
 * - global totals for users, subjects, topics, tasks, exams and study
 *   sessions via database-side count queries
 * - per-role counts, the registration trend and "new this month" from the
 *   user's createdAt timestamp, and the five most recently registered users
 * - the "Complete Your StudyFlow" onboarding cards, scoped to the
 *   authenticated admin's OWN user-owned records (subjects, topics, tasks,
 *   exams and study sessions) so the section never exposes another user's
 *   data; links always point at existing study routes
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy");

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final TaskRepository taskRepository;
    private final ExamRepository examRepository;
    private final StudySessionRepository studySessionRepository;

    /**
     * Constructor injection of the repositories.
     */
    public AdminDashboardServiceImpl(UserRepository userRepository,
                                     SubjectRepository subjectRepository,
                                     TopicRepository topicRepository,
                                     TaskRepository taskRepository,
                                     ExamRepository examRepository,
                                     StudySessionRepository studySessionRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.taskRepository = taskRepository;
        this.examRepository = examRepository;
        this.studySessionRepository = studySessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardData getDashboardData(String adminEmail) {

        User admin = resolve(adminEmail);

        long totalUsers = userRepository.count();
        long activeCount = userRepository.countByEnabled(true);

        Map<Role, Long> roleCounts = countUsersByRole();

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        Map<String, Long> registrations = registrationsByMonth();

        List<String> roleLabels = new ArrayList<>(roleCounts.keySet().stream()
                .map(Role::name)
                .toList());
        List<Long> roleCountValues = new ArrayList<>(roleCounts.values());

        return AdminDashboardData.builder()
                .totalUsers(totalUsers)
                .totalSubjects(subjectRepository.count())
                .totalTopics(topicRepository.count())
                .totalTasks(taskRepository.count())
                .totalExams(examRepository.count())
                .totalStudySessions(studySessionRepository.count())
                .newUsersThisMonth(userRepository.countUsersCreatedSince(monthStart))
                .userCount(roleCounts.getOrDefault(Role.USER, 0L))
                .adminCount(roleCounts.getOrDefault(Role.ADMIN, 0L))
                .activeCount(activeCount)
                .disabledCount(totalUsers - activeCount)
                .roleLabels(roleLabels)
                .roleCounts(roleCountValues)
                .trendLabels(new ArrayList<>(registrations.keySet()))
                .trendCounts(new ArrayList<>(registrations.values()))
                .recentUsers(userRepository.findTop5ByOrderByCreatedAtDesc())
                .setupCards(buildSetupCards(admin))
                .build();
    }

    /**
     * Resolve the authenticated admin from the security context. A missing
     * record is tolerated so the admin pages keep rendering (the onboarding
     * cards simply fall back to the not-started state).
     */
    private User resolve(String adminEmail) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(adminEmail).orElse(null);
    }

    /**
     * Build the "Complete Your StudyFlow" onboarding cards from the
     * authenticated admin's OWN records. Counts use the existing
     * user-scoped queries so one admin can never see another user's data.
     * A card with no records encourages adding the first item; a card with
     * records reports the real count and links to the matching list page.
     */
    private List<AdminSetupCard> buildSetupCards(User admin) {

        long subjects = admin == null ? 0L : subjectRepository.countByUser(admin);
        long topics = admin == null ? 0L : topicRepository.countBySubject_User(admin);
        long tasks = admin == null ? 0L : taskRepository.countBySubject_User(admin);
        long exams = admin == null ? 0L : examRepository.countBySubject_User(admin);
        long sessions = admin == null ? 0L : studySessionRepository.countBySubject_User(admin);

        List<AdminSetupCard> cards = new ArrayList<>();
        cards.add(setupCard("SUBJECT", "Subjects", "bi bi-book-half",
                subjects, "Add Your First Subject", "Manage Subjects",
                "/subjects/new", "/subjects"));
        cards.add(setupCard("TOPIC", "Topics", "bi bi-journal-text",
                topics, "Add Your First Topic", "Manage Topics",
                "/topics", "/topics"));
        cards.add(setupCard("TASK", "Tasks", "bi bi-check2-square",
                tasks, "Add Your First Task", "Manage Tasks",
                "/tasks/new", "/tasks"));
        cards.add(setupCard("EXAM", "Exams", "bi bi-calendar-event",
                exams, "Add Your First Exam", "Manage Exams",
                "/exams?add=true", "/exams"));
        cards.add(setupCard("SESSION", "Study Sessions", "bi bi-clock-history",
                sessions, "Log Your First Study Session", "View Study Sessions",
                "/study-sessions/new", "/study-sessions"));
        return cards;
    }

    /**
     * Build one onboarding card. When nothing exists the card is in the
     * "not started" state and the CTA opens the add route; when records
     * exist it reports the real count and links to the manage route.
     */
    private AdminSetupCard setupCard(String key, String title, String icon,
                                     long count, String addLabel, String manageLabel,
                                     String addUrl, String manageUrl) {
        boolean done = count > 0;
        String statusLabel = done ? count + " added" : "Not started";
        String ctaLabel = done ? manageLabel : addLabel;
        String ctaUrl = done ? manageUrl : addUrl;
        return new AdminSetupCard(key, title, icon, count, done,
                statusLabel, ctaLabel, ctaUrl);
    }

    /**
     * Count registered users grouped by role, preserving enum order.
     */
    private Map<Role, Long> countUsersByRole() {
        Map<Role, Long> counts = new EnumMap<>(Role.class);
        for (Object[] row : userRepository.countUsersByRole()) {
            counts.put((Role) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Count registrations grouped by year-month and format the keys as
     * human-readable chart labels ("Aug 2026").
     */
    private Map<String, Long> registrationsByMonth() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : userRepository.countRegistrationsByMonth()) {
            counts.put(formatMonthLabel(String.valueOf(row[0])),
                    ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Convert a "yyyy-MM" string from the database into a display label.
     */
    private String formatMonthLabel(String yearMonth) {
        try {
            return LocalDate.parse(yearMonth + "-01").format(MONTH_LABEL);
        } catch (Exception e) {
            return yearMonth;
        }
    }
}