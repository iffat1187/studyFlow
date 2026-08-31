package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.CalendarEventDto;
import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.service.CalendarService;
import com.studyflow.studyflow.service.SubjectService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the authenticated user's unified calendar page.
 *
 * Thin by convention: the controller resolves the authenticated user's
 * email from the security context, applies the (optional) date range, and
 * delegates all event aggregation to {@link CalendarService}. No user
 * identifier is ever read from the request — the security context is the
 * single source of truth for ownership.
 *
 * The subject filter is likewise populated from the backend via
 * {@link SubjectService}, so a user can only ever see and filter by their
 * own subjects (IDOR prevention).
 *
 * The default range is the current calendar month when no dates are given.
 */
@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final SubjectService subjectService;

    /**
     * Constructor injection.
     */
    public CalendarController(CalendarService calendarService, SubjectService subjectService) {
        this.calendarService = calendarService;
        this.subjectService = subjectService;
    }

    /**
     * Show the calendar page for the current month (or the requested range).
     *
     * @param authentication the security context (never null on this route)
     * @param startDate      optional range start (defaults to month start)
     * @param endDate        optional range end (defaults to month end)
     * @param model          the Thymeleaf model
     * @return the calendar template
     */
    @GetMapping
    public String calendar(Authentication authentication,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                           Model model) {
        LocalDate today = LocalDate.now();
        LocalDate from = resolveRangeStart(startDate, today);
        LocalDate to = resolveRangeEnd(endDate, today, from);
        List<CalendarEventDto> events = calendarService.getCalendarEvents(authentication.getName(), from, to);
        List<SubjectDto> subjects = subjectService.getAllSubjects(authentication.getName());

        model.addAttribute("events", events);
        model.addAttribute("subjects", subjects);
        model.addAttribute("startDate", from);
        model.addAttribute("endDate", to);
        return "calendar";
    }

    /**
     * JSON endpoint backing a future calendar AJAX view.
     *
     * Returns the aggregation as a plain JSON list so a front-end calendar
     * can fetch events lazily without a full page reload.
     *
     * @param authentication the security context (never null on this route)
     * @param startDate      optional range start
     * @param endDate        optional range end
     * @return the calendar events as JSON
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> events(Authentication authentication,
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(calendarService.getCalendarEvents(authentication.getName(), startDate, endDate));
    }

    private LocalDate resolveRangeStart(LocalDate requested, LocalDate today) {
        return requested != null ? requested : today.withDayOfMonth(1);
    }

    private LocalDate resolveRangeEnd(LocalDate requested, LocalDate today, LocalDate start) {
        if (requested != null && !requested.isBefore(start)) {
            return requested;
        }
        return start.withDayOfMonth(start.lengthOfMonth());
    }
}
