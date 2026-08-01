package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * Controller responsible for handling all Subject related requests.
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
     * Display all subjects.
     */
    @GetMapping
    public String listSubjects(Model model) {

        model.addAttribute("subjects", subjectService.getAllSubjects());

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
                ""
        ));

        return "subject/add-subject";
    }

    /**
     * Save a new Subject.
     */
    @PostMapping("/save")
    public String saveSubject(
            @Valid @ModelAttribute("subjectDto") SubjectDto subjectDto,
            BindingResult bindingResult
    ) {

        // If validation fails, return to the form.
        if (bindingResult.hasErrors()) {
            return "subject/add-subject";
        }

        subjectService.saveSubject(subjectDto);

        return "redirect:/subjects";
    }

    /**
     * Display Edit Subject form.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Model model
    ) {

        model.addAttribute("subjectDto",
                subjectService.getSubjectById(id));

        return "subject/edit-subject";
    }

    /**
     * Update an existing Subject.
     */
    @PostMapping("/update/{id}")
    public String updateSubject(
            @PathVariable Long id,
            @Valid @ModelAttribute("subjectDto") SubjectDto subjectDto,
            BindingResult bindingResult
    ) {

        if (bindingResult.hasErrors()) {
            return "subject/edit-subject";
        }

        subjectService.updateSubject(id, subjectDto);

        return "redirect:/subjects";
    }

    /**
     * Delete a subject.
     */
    @GetMapping("/delete/{id}")
    public String deleteSubject(@PathVariable Long id) {

        subjectService.deleteSubject(id);

        return "redirect:/subjects";
    }

}
