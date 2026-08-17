package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.AdminDashboardData;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.repository.SubjectRepository;
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
 * - total users, subjects and per-role counts via {@link UserRepository}
 *   / {@link SubjectRepository}
 * - the registration trend and "new this month" from the user's
 *   createdAt timestamp
 * - the five most recently registered users
 *
 * Parentheses task and exam statistics are deliberately omitted because
 * those modules are not part of the project yet.
 */
@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy");

    private final UserRepository userRepository;
    private final SubjectRepository subjectRepository;

    /**
     * Constructor injection of the repositories.
     */
    public AdminDashboardServiceImpl(UserRepository userRepository,
                                     SubjectRepository subjectRepository) {
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardData getDashboardData() {

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
                .build();
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