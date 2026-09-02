package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.AdminDashboardData;
import com.studyflow.studyflow.dto.AdminSetupCard;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the admin dashboard aggregation queries execute against the
 * real database schema (no stubs). Values are real and therefore only
 * consistency invariants are asserted, never specific numbers.
 */
@SpringBootTest
@Transactional
class AdminDashboardQueryIntegrationTest {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Test
    void dashboardData_isAggregatedFromRealDatabase() {
        AdminDashboardData data = adminDashboardService.getDashboardData("admin-dashboard@example.com");

        assertNotNull(data);
        assertTrue(data.getTotalUsers() >= 0);
        assertTrue(data.getTotalSubjects() >= 0);
        assertTrue(data.getTotalTopics() >= 0);
        assertTrue(data.getTotalTasks() >= 0);
        assertTrue(data.getTotalExams() >= 0);
        assertTrue(data.getTotalStudySessions() >= 0);
        assertTrue(data.getNewUsersThisMonth() >= 0);
        assertTrue(data.getUserCount() >= 0);
        assertTrue(data.getAdminCount() >= 0);
        assertTrue(data.getActiveCount() >= 0);
        assertTrue(data.getDisabledCount() >= 0);

        // count() and the role GROUP BY must agree on the same pool.
        assertEquals(data.getTotalUsers(), data.getUserCount() + data.getAdminCount());

        // Every account is either enabled or disabled.
        assertEquals(data.getTotalUsers(), data.getActiveCount() + data.getDisabledCount());

        // Chart series must be aligned.
        assertEquals(data.getTrendLabels().size(), data.getTrendCounts().size());
        assertEquals(data.getRoleLabels().size(), data.getRoleCounts().size());

        // Recent users is capped at five.
        assertTrue(data.getRecentUsers().size() <= 5);
        for (User user : data.getRecentUsers()) {
            assertNotNull(user.getName());
            assertNotNull(user.getEmail());
        }

        // One onboarding card per study module, each with real, non-negative
        // counts and a usable existing route.
        assertNotNull(data.getSetupCards());
        assertEquals(5, data.getSetupCards().size());
        for (AdminSetupCard card : data.getSetupCards()) {
            assertNotNull(card.key());
            assertNotNull(card.title());
            assertNotNull(card.icon());
            assertTrue(card.count() >= 0);
            assertEquals(card.count() > 0, card.done());
            assertNotNull(card.statusLabel());
            assertNotNull(card.ctaLabel());
            assertNotNull(card.ctaUrl());
            assertTrue(card.ctaUrl().startsWith("/"));
        }
    }
}