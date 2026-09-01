package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.NotificationViewDto;
import com.studyflow.studyflow.service.NotificationService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Populates the navbar notification bell for every authenticated page.
 *
 * Because the top navbar is a shared fragment rendered inside the global
 * layout on every authenticated view, this advice injects the current user's
 * unread count and compact recent list into the model once per request. This
 * keeps the bell badge correct on page load without any AJAX round-trip and
 * without broad {@code findAll()} queries — every value is user-scoped.
 *
 * On public/anonymous requests (login, registration, landing) no notification
 * data is computed: the bell is not shown there anyway.
 */
@ControllerAdvice
public class NotificationNavbarAdvice {

    private static final int DROPDOWN_LIMIT = 5;

    private final NotificationService notificationService;

    /**
     * Constructor injection.
     */
    public NotificationNavbarAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * The authenticated user's unread notification count for the bell badge.
     *
     * @param authentication security context (anonymous on public pages)
     * @return the unread count, or 0 when not authenticated
     */
    @ModelAttribute("navbarUnreadCount")
    public long navbarUnreadCount(Authentication authentication) {
        String email = authenticatedEmail(authentication);
        if (email == null) {
            return 0;
        }
        return notificationService.getUnreadCount(email);
    }

    /**
     * The authenticated user's recent notifications for the dropdown.
     *
     * @param authentication security context (anonymous on public pages)
     * @return the user's recent notifications, or an empty list, never null
     */
    @ModelAttribute("navbarRecentNotifications")
    public List<NotificationViewDto> navbarRecentNotifications(Authentication authentication) {
        String email = authenticatedEmail(authentication);
        if (email == null) {
            return List.of();
        }
        return notificationService.getRecentNotifications(email, DROPDOWN_LIMIT).stream()
                .map(NotificationViewDto::from)
                .toList();
    }

    /**
     * The authenticated principal's email, or null when the request is
     * anonymous or the principal is not the app's User entity.
     */
    private String authenticatedEmail(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}
