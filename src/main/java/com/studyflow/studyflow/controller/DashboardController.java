package com.studyflow.studyflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller responsible for the authenticated user's dashboard.
 *
 * Minimal route that renders the dashboard template. Detailed dashboard
 * content and role-based presentation belong to a later phase.
 */
@Controller
public class DashboardController {

    /**
     * Display the dashboard.
     *
     * @return the dashboard template name
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
