package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.StudySessionDto;
import com.studyflow.studyflow.dto.TopicDto;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.service.StudySessionService;
import com.studyflow.studyflow.service.SubjectService;
import com.studyflow.studyflow.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller responsible for the Study Session creation flow.
 *
 * The controller stays thin: it resolves the authenticated user's email
 * from the security context and delegates every ownership-aware operation
 * to {@link StudySessionService}. No user identifier is ever read from the
 * request payload or URL. A submitted subjectId/topicId is only trusted
 * after the service verifies the subject belongs to the user and any topic
 * belongs to that exact subject.
 *
 * Only the authenticated user's own subjects and topics are placed on the
 * form, so a user can never choose another user's subject or topic.
 */
@Controller
@RequestMapping("/study-sessions")
public class StudySessionController {

    private final StudySessionService studySessionService;
    private final SubjectService subjectService;
    private final TopicService topicService;

    /**
     * Constructor Injection.
     */
    public StudySessionController(StudySessionService studySessionService,
                                  SubjectService subjectService,
                                  TopicService topicService) {
        this.studySessionService = studySessionService;
        this.subjectService = subjectService;
        this.topicService = topicService;
    }

    /**
     * Show the Study Sessions list page.
     *
     * Only the authenticated user's sessions are listed (queried via their
     * subjects) and only the user's own subjects are exposed in the create
     * modal. The user's topics are embedded grouped by subject so the modal
     * can filter them client-side when the subject changes — a user can never
     * trigger loading of another user's topics.
     */
    @GetMapping
    public String listStudySessions(
            @RequestParam(value = "add", required = false) Boolean add,
            Authentication authentication,
            Model model) {

        String userEmail = authentication.getName();

        List<StudySessionDto> sessions = studySessionService.getAllSessions(userEmail);

        model.addAttribute("sessions", sessions);
        model.addAttribute("sessionGroups", groupSessionsByMonth(sessions));
        model.addAttribute("subjects", subjectService.getAllSubjects(userEmail));
        model.addAttribute("topicsBySubject", userTopicsBySubject(userEmail));
        model.addAttribute("studySessionDto", defaultStudySessionDto(null));
        model.addAttribute("showSessionModal", Boolean.TRUE.equals(add));
        model.addAttribute("today", LocalDate.now());

        return "study-sessions/list-study-session";
    }

    /**
     * Deep link that opens the Study Sessions list page with the Start Study
     * Session modal already open (mirrors how Exams work with ?add=true).
     *
     * An optional subjectId pre-selects that subject in the form — but only
     * if it belongs to the user: the select is populated exclusively from the
     * user-scoped subjects list, so a foreign id simply matches no option.
     */
    @GetMapping("/new")
    public String showAddForm(
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            Authentication authentication,
            Model model) {

        String userEmail = authentication.getName();

        List<StudySessionDto> sessions = studySessionService.getAllSessions(userEmail);

        model.addAttribute("sessions", sessions);
        model.addAttribute("sessionGroups", groupSessionsByMonth(sessions));
        model.addAttribute("subjects", subjectService.getAllSubjects(userEmail));
        model.addAttribute("topicsBySubject", userTopicsBySubject(userEmail));
        model.addAttribute("studySessionDto", defaultStudySessionDto(subjectId));
        model.addAttribute("showSessionModal", true);
        model.addAttribute("today", LocalDate.now());

        return "study-sessions/list-study-session";
    }

    /**
     * Save a new Study Session for the authenticated user.
     *
     * Subject and session type stay strongly typed. Validation reloads the
     * user's subjects/topics and returns the form WITHOUT redirecting, so
     * the user keeps their submitted values and sees the field errors.
     *
     * On success the creation is delegated to the service (which re-verifies
     * subject ownership and Topic↔Subject consistency) and the user is sent
     * to the Study Sessions route using Post/Redirect/Get.
     */
    @PostMapping("/save")
    public String saveStudySession(
            @Valid @ModelAttribute("studySessionDto") StudySessionDto studySessionDto,
            BindingResult bindingResult,
            WebRequest request,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String userEmail = authentication.getName();

        // If validation fails, return to the form with the dropdowns reloaded
        // and the submitted values preserved (never redirect on failure).
        if (bindingResult.hasErrors()) {
            List<StudySessionDto> sessions = studySessionService.getAllSessions(userEmail);
            model.addAttribute("sessions", sessions);
            model.addAttribute("sessionGroups", groupSessionsByMonth(sessions));
            model.addAttribute("subjects", subjectService.getAllSubjects(userEmail));
            model.addAttribute("topicsBySubject", userTopicsBySubject(userEmail));
            model.addAttribute("showSessionModal", true);
            model.addAttribute("today", LocalDate.now());
            // A binding failure (for example a non-numeric duration) can leave
            // the constructor-bound record attribute as null; rebuild it from
            // the raw request so the form re-render keeps the user's input.
            model.addAttribute("studySessionDto", studySessionDto != null
                    ? studySessionDto
                    : rebuildStudySessionDto(request, null));
            // When the attribute could not be constructed, Spring MVC may not
            // register its BindingResult in the model; re-key the field errors
            // so #fields/th:errors can still render them on the re-rendered form.
            if (!model.containsAttribute(BindingResult.MODEL_KEY_PREFIX + "studySessionDto")) {
                model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "studySessionDto", bindingResult);
            }
            return "study-sessions/list-study-session";
        }

        studySessionService.saveSession(studySessionDto, userEmail);

        redirectAttributes.addFlashAttribute("successMessage", "Study session created successfully!");

        return "redirect:/study-sessions";
    }

    /**
     * Show the Edit Study Session page for a session the authenticated user owns.
     *
     * The session is resolved exclusively through the user-scoped service
     * lookup (User → Subject → StudySession), which throws
     * AccessDeniedException for a missing or foreign session — no unrestricted
     * findById is ever used for user-facing flows. Only the user's own subjects
     * and (subject-grouped) topics are placed on the form.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        String userEmail = authentication.getName();

        model.addAttribute("studySessionDto", studySessionService.getSessionById(id, userEmail));
        model.addAttribute("subjects", subjectService.getAllSubjects(userEmail));
        model.addAttribute("topicsBySubject", userTopicsBySubject(userEmail));

        return "study-sessions/edit-study-session";
    }

    /**
     * Update an existing Study Session for the authenticated user.
     *
     * Validation follows the create flow exactly: on failure the form re-renders
     * WITHOUT redirecting, keeping the submitted values and field errors, with
     * the user's subjects/topics reloaded. The ownership chain (session → subject
     * → optional topic) is re-verified by the service before any write.
     */
    @PostMapping("/update/{id}")
    public String updateStudySession(
            @PathVariable Long id,
            @Valid @ModelAttribute("studySessionDto") StudySessionDto studySessionDto,
            BindingResult bindingResult,
            WebRequest request,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String userEmail = authentication.getName();

        if (bindingResult.hasErrors()) {
            model.addAttribute("subjects", subjectService.getAllSubjects(userEmail));
            model.addAttribute("topicsBySubject", userTopicsBySubject(userEmail));
            // Mirror the create flow: a binding failure can leave the
            // constructor-bound record attribute null; rebuild it (keeping the
            // path id so the re-rendered form targets the same session).
            model.addAttribute("studySessionDto", studySessionDto != null
                    ? studySessionDto
                    : rebuildStudySessionDto(request, id));
            if (!model.containsAttribute(BindingResult.MODEL_KEY_PREFIX + "studySessionDto")) {
                model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "studySessionDto", bindingResult);
            }
            return "study-sessions/edit-study-session";
        }

        studySessionService.updateSession(id, studySessionDto, userEmail);

        redirectAttributes.addFlashAttribute("successMessage", "Study session updated successfully!");

        return "redirect:/study-sessions";
    }

    /**
     * Delete the authenticated user's Study Session.
     *
     * Delegated to the service, which verifies ownership first via the
     * user-scoped lookup before the destructive operation. Never reaches the
     * repository for a foreign or missing session.
     */
    @PostMapping("/delete/{id}")
    public String deleteStudySession(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String userEmail = authentication.getName();

        studySessionService.deleteSession(id, userEmail);

        redirectAttributes.addFlashAttribute("successMessage", "Study session deleted successfully!");

        return "redirect:/study-sessions";
    }

    /**
     * Group the user's sessions by the month (derived from the session date)
     * for the history presentation. Grouping is stable: month buckets keep the
     * order of the underlying soonest-first session list.
     */
    private Map<LocalDate, List<StudySessionDto>> groupSessionsByMonth(List<StudySessionDto> sessions) {
        return sessions.stream()
                .collect(Collectors.groupingBy(
                        dto -> dto.sessionDate().withDayOfMonth(1),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * The authenticated user's topics grouped by subject id.
     *
     * The entire group is built from the user-scoped {@code getAllTopics},
     * so only the user's own topics are ever embedded into the page and no
     * user can read or select another user's topics (IDOR prevention).
     */
    private Map<Long, List<TopicDto>> userTopicsBySubject(String userEmail) {
        return topicService.getAllTopics(userEmail).stream()
                .collect(Collectors.groupingBy(TopicDto::subjectId));
    }

    /**
     * A blank Create Study Session form: today's date, a 60-minute default
     * duration and FOCUS as the default type. No pre-filled subject, so the
     * user makes an explicit choice.
     */
    private StudySessionDto defaultStudySessionDto(Long subjectId) {
        return new StudySessionDto(
                null,
                subjectId,
                null,
                null,
                null,
                LocalDate.now(),
                null,
                null,
                60,
                StudySessionType.FOCUS,
                ""
        );
    }

    /**
     * Rebuild the form-backing DTO from the raw request when a binding
     * failure prevented Spring from constructing the record attribute
     * (e.g. a non-numeric duration). Keeps the user's submitted values
     * visible on the re-rendered form; a non-numeric duration stays null.
     */
    private StudySessionDto rebuildStudySessionDto(WebRequest request, Long id) {
        return new StudySessionDto(
                id,
                parseLong(request.getParameter("subjectId")),
                parseLong(request.getParameter("topicId")),
                null,
                null,
                parseDate(request.getParameter("sessionDate")),
                parseTime(request.getParameter("startTime")),
                parseTime(request.getParameter("endTime")),
                parseInt(request.getParameter("durationMinutes")),
                parseType(request.getParameter("sessionType")),
                request.getParameter("notes")
        );
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static StudySessionType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StudySessionType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}