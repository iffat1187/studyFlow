package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.service.AnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for the authenticated user's study analytics page.
 *
 * Thin by convention: the controller resolves the authenticated user's
 * email from the security context and delegates all calculations to
 * {@link AnalyticsService}, which scopes every query to that user. No user
 * identifier is ever read from the request.
 */
@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Constructor injection.
     */
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Show the analytics page for the authenticated user.
     *
     * @param authentication the security context (never null on this route)
     * @param model          the Thymeleaf model
     * @return the analytics template
     */
    @GetMapping
    public String analytics(Authentication authentication, Model model) {
        model.addAttribute("data", analyticsService.getAnalytics(authentication.getName()));
        return "analytics/analytics";
    }
}