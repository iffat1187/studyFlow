package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller responsible for handling all Subject related requests.
 *
 * The controller stays thin: it resolves the authenticated user's email
 * from the security context and delegates every ownership-aware operation
 * to {@link SubjectService}. No user identifier is ever read from the
 * request payload or URL.
 */
@Controller
@RequestMapping("/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    /**
     * Constructor Injection.
     */
    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    /**
     * Display the subjects owned by the authenticated user.
     */
    @GetMapping
    public String listSubjects(Authentication authentication, Model model) {

        model.addAttribute("subjects",
                subjectService.getAllSubjects(authentication.getName()));

        return "subject/list-subject";
    }

    /**
     * Display Add Subject form.
     */
    @GetMapping("/new")
    public String showAddForm(Model model) {

        model.addAttribute("subjectDto", new SubjectDto(
                null,
                "",
                "",
                "",
                0L
        ));

        return "subject/add-subject";
    }

    /**
     * Save a new Subject, assigned to the authenticated user.
     */
    @PostMapping("/save")
    public String saveSubject(
            @Valid @ModelAttribute("subjectDto") SubjectDto subjectDto,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        // If validation fails, return to the form.
        if (bindingResult.hasErrors()) {
            return "subject/add-subject";
        }

        subjectService.saveSubject(subjectDto, authentication.getName());

        redirectAttributes.addFlashAttribute("successMessage", "Subject created successfully!");
        return "redirect:/subjects";
    }

    /**
     * Display Edit Subject form for a subject owned by the authenticated user.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Model model,
            Authentication authentication
    ) {

        model.addAttribute("subjectDto",
                subjectService.getSubjectById(id, authentication.getName()));

        return "subject/edit-subject";
    }

    /**
     * Update an existing Subject, verifying ownership first.
     */
    @PostMapping("/update/{id}")
    public String updateSubject(
            @PathVariable Long id,
            @Valid @ModelAttribute("subjectDto") SubjectDto subjectDto,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        if (bindingResult.hasErrors()) {
            return "subject/edit-subject";
        }

        subjectService.updateSubject(id, subjectDto, authentication.getName());

        redirectAttributes.addFlashAttribute("successMessage", "Subject updated successfully!");
        return "redirect:/subjects";
    }

    /**
     * Delete a subject owned by the authenticated user.
     */
    @GetMapping("/delete/{id}")
    public String deleteSubject(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        subjectService.deleteSubject(id, authentication.getName());

        redirectAttributes.addFlashAttribute("successMessage", "Subject deleted successfully!");
        return "redirect:/subjects";
    }

}