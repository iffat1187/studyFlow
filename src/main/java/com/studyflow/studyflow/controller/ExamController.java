package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.service.ExamService;
import com.studyflow.studyflow.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller responsible for the Exam list page and Exam creation.
 *
 * The controller stays thin: it resolves the authenticated user's email
 * from the security context and delegates every ownership-aware operation
 * to {@link ExamService}. No user identifier is ever read from the
 * request payload or URL, and the submitted subjectId is only trusted
 * after the service verifies it belongs to the current user.
 *
 * The Add Exam form is rendered inside a modal on the list page; on a
 * validation failure the page is re-rendered with the modal open so the
 * user keeps their input and sees the field errors.
 */
@Controller
@RequestMapping("/exams")
public class ExamController {

    private final ExamService examService;
    private final SubjectService subjectService;

    /**
     * Constructor Injection.
     */
    public ExamController(ExamService examService, SubjectService subjectService) {
        this.examService = examService;
        this.subjectService = subjectService;
    }

    /**
     * Display the authenticated user's exams.
     *
     * Supports search, subject filter, exam type filter and an Upcoming/Past
     * view. Every combination is resolved through user-scoped service queries,
     * and the only subjects offered for filtering/creation are the user's own.
     */
    @GetMapping
    public String listExams(
            @RequestParam(value = "view", defaultValue = "upcoming") String view,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "type", required = false) ExamType examType,
            @RequestParam(value = "add", defaultValue = "false") boolean add,
            Authentication authentication,
            Model model) {

        String userEmail = authentication.getName();
        boolean upcoming = !"past".equals(view);

        List<ExamDto> exams = examService.getExams(userEmail, search, subjectId, examType, upcoming);
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        populateListModel(model, subjects, exams, search, subjectId, examType, view);

        // The dashboard's "+ Add Exam" links to /exams?add=true so the real
        // creation modal opens already, instead of showing a fake example.
        model.addAttribute("examDto", defaultExamDto());
        model.addAttribute("showExamModal", add);

        return "exam/list-exam";
    }

    /**
     * Save a new Exam, assigned to the subject (owned by the authenticated user).
     *
     * The exam type stays strongly typed (ExamType); the service verifies the
     * submitted subjectId belongs to the user before persisting anything.
     * On validation errors the list page is re-rendered with the Add Exam
     * modal open, showing the field errors without losing the user's input.
     */
    @PostMapping("/save")
    public String saveExam(
            @Valid @ModelAttribute("examDto") ExamDto examDto,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String userEmail = authentication.getName();

        if (bindingResult.hasErrors()) {
            List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);
            List<ExamDto> exams = examService.getExams(userEmail, null, null, null, true);

            populateListModel(model, subjects, exams, null, null, null, "upcoming");
            model.addAttribute("showExamModal", true);

            return "exam/list-exam";
        }

        examService.saveExam(examDto, examDto.subjectId(), userEmail);

        redirectAttributes.addFlashAttribute("successMessage", "Exam created successfully!");

        return "redirect:/exams";
    }

    /**
     * Display the Edit Exam form for an exam owned by the authenticated user.
     *
     * The exam is loaded through the user-scoped service lookup, so a request
     * for another user's exam id never populates the form (IDOR prevention).
     * The Subject dropdown is built from the authenticated user's own subjects.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        String userEmail = authentication.getName();
        ExamDto examDto = examService.getExamById(id, userEmail);
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        model.addAttribute("examDto", examDto);
        model.addAttribute("subjects", subjects);

        return "exam/edit-exam";
    }

    /**
     * Update an existing Exam, verifying ownership before applying changes.
     *
     * The exam itself and the selected (possibly new) subject must both belong
     * to the authenticated user. On validation errors the edit form is returned
     * with the user's entered values preserved and the subject list reloaded.
     */
    @PostMapping("/update/{id}")
    public String updateExam(
            @PathVariable Long id,
            @Valid @ModelAttribute("examDto") ExamDto examDto,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String userEmail = authentication.getName();

        if (bindingResult.hasErrors()) {
            List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);
            model.addAttribute("subjects", subjects);
            return "exam/edit-exam";
        }

        examService.updateExam(id, examDto, userEmail);

        redirectAttributes.addFlashAttribute("successMessage", "Exam updated successfully!");

        return "redirect:/exams";
    }

    /**
     * Delete an Exam owned by the authenticated user.
     *
     * Ownership is verified by the service before the destructive operation;
     * a request targeting another user's exam is rejected (IDOR prevention).
     */
    @PostMapping("/delete/{id}")
    public String deleteExam(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        examService.deleteExam(id, authentication.getName());

        redirectAttributes.addFlashAttribute("successMessage", "Exam deleted successfully!");

        return "redirect:/exams";
    }

    /**
     * Populate the model attributes shared by every render of the list page.
     */
    private void populateListModel(Model model, List<SubjectDto> subjects, List<ExamDto> exams,
                                   String search, Long subjectId, ExamType examType, String view) {
        model.addAttribute("exams", exams);
        model.addAttribute("subjects", subjects);
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("selectedType", examType);
        model.addAttribute("searchTerm", search);
        model.addAttribute("currentView", view);
        model.addAttribute("today", LocalDate.now());
    }

    /**
     * A blank exam for the Add Exam modal: no pre-filled values, so the user
     * makes an explicit choice for every required field.
     */
    private ExamDto defaultExamDto() {
        return new ExamDto(
                null,
                null,
                null,
                "",
                null,
                null,
                null,
                60,
                ""
        );
    }

}