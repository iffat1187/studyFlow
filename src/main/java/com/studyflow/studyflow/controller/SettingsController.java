package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.UserSettingsDto;
import com.studyflow.studyflow.service.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for the authenticated user's Study Preferences (Settings page).
 *
 * The controller stays thin: it delegates to {@link UserSettingsService},
 * which resolves the authenticated user from the Spring Security context.
 * No user identifier is ever read from the request payload or URL, so one
 * user can never create, read or modify another user's settings (IDOR
 * prevention).
 *
 * Validation failures return the form WITHOUT redirecting, keeping the
 * submitted values and field errors visible. Success uses Post/Redirect/Get
 * with a flash message.
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UserSettingsService userSettingsService;

    /**
     * Constructor injection.
     *
     * @param userSettingsService per-user settings service
     */
    public SettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    /**
     * Show the Settings page with the current user's preferences.
     *
     * @param model the Thymeleaf model
     * @return the settings template
     */
    @GetMapping
    public String settings(Model model) {
        model.addAttribute("userSettingsDto", userSettingsService.getCurrentUserSettings());
        return "settings/settings";
    }

    /**
     * Persist the authenticated user's study preferences.
     *
     * Only the authenticated user's own settings can be saved (the service
     * resolves the owner), and only the approved preference fields are
     * written — identity, credentials, role and notification preferences are
     * never touched.
     *
     * @param userSettingsDto   the submitted settings (validated)
     * @param bindingResult     Bean Validation results
     * @param model             the Thymeleaf model (re-render on failure)
     * @param redirectAttributes flash attributes for the success toast
     * @return the settings template on error, PRG redirect on success
     */
    @PostMapping
    public String updateSettings(
            @Valid @ModelAttribute("userSettingsDto") UserSettingsDto userSettingsDto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            // Re-render the form WITHOUT redirecting so the user keeps their
            // submitted values and sees the field errors. A binding failure
            // (e.g. an invalid enum value) can leave the constructor-bound
            // record attribute null; fall back to the persisted settings.
            model.addAttribute("userSettingsDto", userSettingsDto != null
                    ? userSettingsDto
                    : userSettingsService.getCurrentUserSettings());
            if (!model.containsAttribute(BindingResult.MODEL_KEY_PREFIX + "userSettingsDto")) {
                model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "userSettingsDto", bindingResult);
            }
            return "settings/settings";
        }

        userSettingsService.updateCurrentUserSettings(userSettingsDto);

        redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully.");

        return "redirect:/settings";
    }
}