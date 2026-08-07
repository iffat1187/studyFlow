package com.studyflow.studyflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller responsible for the public StudyFlow landing page.
 *
 * The landing page is the public homepage of the application.
 * Authenticated areas (dashboard, subjects) will be protected by
 * Spring Security in a later phase.
 */
@Controller
public class LandingController {

    /**
     * Serve the public landing page.
     */
    @GetMapping("/")
    public String landing() {
        return "landing/index";
    }

}
