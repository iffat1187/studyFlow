package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.dto.NotificationPreferenceDto;
import com.studyflow.studyflow.dto.NotificationViewDto;
import com.studyflow.studyflow.service.NotificationPreferenceService;
import com.studyflow.studyflow.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for the authenticated user's notification center.
 *
 * Thin by convention: the controller resolves the authenticated user's email
 * from the security context (never from the request) and delegates to
 * {@link NotificationService}. Every read and write is user-scoped, so one
 * user can never see or modify another user's notifications (IDOR
 * prevention).
 *
 * Routes served here are all secured in SecurityConfig — no public access is
 * granted. The full list lives on the {@code /notifications} page; the
 * navbar bell/dropdown is populated on every authenticated page load by a
 * global model-attribute advice.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private static final int DROPDOWN_LIMIT = 5;

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    /**
     * Constructor injection.
     */
    public NotificationController(NotificationService notificationService,
                                  NotificationPreferenceService preferenceService) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
    }

    /**
     * Full notification history page, newest first.
     *
     * @param authentication the security context (never null on this route)
     * @param model          the Thymeleaf model
     * @return the notification template
     */
    @GetMapping
    public String notifications(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("notifications", toViews(notificationService.getNotifications(email)));
        model.addAttribute("unreadCount", notificationService.getUnreadCount(email));
        return "notification";
    }

    /**
     * The authenticated user's notification preference settings page.
     *
     * @param authentication the security context
     * @param model          the Thymeleaf model
     * @return the notification settings template
     */
    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        model.addAttribute("prefs", preferenceService.getPreferences(authentication.getName()));
        return "notification-settings";
    }

    /**
     * Persist the authenticated user's notification preferences.
     *
     * Every toggle is read as an explicit {@code false}-defaulted parameter
     * (unchecked checkboxes are absent and thus false). The route operates on
     * the authenticated user only — no user id is accepted from the browser,
     * so another user's preferences can never be modified (IDOR prevention).
     *
     * @param authentication     the security context
     * @param redirectAttributes flash attributes for the success toast
     * @return redirect back to the settings page
     */
    @PostMapping("/settings")
    public String saveSettings(Authentication authentication,
                               @RequestParam(defaultValue = "false") boolean taskDueEnabled,
                               @RequestParam(defaultValue = "false") boolean taskOverdueEnabled,
                               @RequestParam(defaultValue = "false") boolean examUpcomingEnabled,
                               @RequestParam(defaultValue = "false") boolean examTodayEnabled,
                               @RequestParam(defaultValue = "false") boolean studyReminderEnabled,
                               @RequestParam(defaultValue = "false") boolean streakReminderEnabled,
                               @RequestParam(defaultValue = "false") boolean systemEnabled,
                               RedirectAttributes redirectAttributes) {
        preferenceService.updatePreferences(authentication.getName(),
                new NotificationPreferenceDto(
                        taskDueEnabled, taskOverdueEnabled, examUpcomingEnabled,
                        examTodayEnabled, studyReminderEnabled, streakReminderEnabled,
                        systemEnabled));
        redirectAttributes.addFlashAttribute("successMessage", "Notification preferences saved!");
        return "redirect:/notifications/settings";
    }

    /**
     * Compact recent list for the navbar dropdown, limit {@value DROPDOWN_LIMIT}.
     *
     * @param authentication the security context
     * @return the user's newest notifications as enriched view DTOs
     */
    @GetMapping("/recent")
    public ResponseEntity<List<NotificationViewDto>> recent(Authentication authentication) {
        return ResponseEntity.ok(toViews(
                notificationService.getRecentNotifications(authentication.getName(), DROPDOWN_LIMIT)));
    }

    /**
     * The current user's unread count for the navbar badge.
     *
     * @param authentication the security context
     * @return the unread count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(authentication.getName())));
    }

    /**
     * Mark one notification as read, only when it belongs to the current
     * user. A notification id that is unknown or belongs to another user is
     * rejected (404) and the record is left untouched.
     *
     * @param authentication the security context
     * @param id             the notification primary key
     * @return 200 when marked read, 404 when not found/not owned
     */
    @PostMapping("/read/{id}")
    public ResponseEntity<?> markRead(Authentication authentication, @PathVariable Long id) {
        boolean success = notificationService.markAsRead(authentication.getName(), id);
        if (!success) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Mark all of the current user's notifications as read.
     *
     * @param authentication the security context
     * @return the number of notifications flipped to read
     */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        int updated = notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private List<NotificationViewDto> toViews(List<NotificationDto> dtos) {
        return dtos.stream().map(NotificationViewDto::from).toList();
    }
}
