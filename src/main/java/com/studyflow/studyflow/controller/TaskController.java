package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.dto.TaskDto;
import com.studyflow.studyflow.service.SubjectService;
import com.studyflow.studyflow.service.TaskService;
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
 * Controller responsible for handling all Task related requests.
 *
 * The controller stays thin: it resolves the authenticated user's email
 * from the security context and delegates every ownership-aware operation
 * to {@link TaskService}. No user identifier is ever read from the
 * request payload or URL.
 */
@Controller
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final SubjectService subjectService;

    /**
     * Constructor Injection.
     */
    public TaskController(TaskService taskService, SubjectService subjectService) {
        this.taskService = taskService;
        this.subjectService = subjectService;
    }

    /**
     * Display the tasks owned by the authenticated user.
     */
    @GetMapping
    public String listTasks(
            @RequestParam(value = "view", defaultValue = "upcoming") String view,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "search", required = false) String search,
            Authentication authentication,
            Model model
    ) {

        String userEmail = authentication.getName();
        List<TaskDto> tasks;
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        // Build the task list based on filters
        if (search != null && !search.trim().isEmpty()) {
            if (subjectId != null) {
                tasks = taskService.searchTasksBySubject(subjectId, userEmail, search.trim());
            } else {
                tasks = taskService.searchTasks(userEmail, search.trim());
            }
        } else if (subjectId != null) {
            if ("past".equals(view)) {
                tasks = taskService.getPastTasksBySubject(subjectId, userEmail);
            } else {
                tasks = taskService.getUpcomingTasksBySubject(subjectId, userEmail);
            }
        } else {
            if ("past".equals(view)) {
                tasks = taskService.getPastTasks(userEmail);
            } else {
                tasks = taskService.getUpcomingTasks(userEmail);
            }
        }

        model.addAttribute("tasks", tasks);
        model.addAttribute("subjects", subjects);
        model.addAttribute("selectedSubjectId", subjectId);
        model.addAttribute("searchTerm", search);
        model.addAttribute("currentView", view);
        model.addAttribute("today", LocalDate.now());

        return "task/list-task";
    }

    /**
     * Display Add Task form.
     */
    @GetMapping("/new")
    public String showAddForm(
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            Authentication authentication,
            Model model
    ) {

        String userEmail = authentication.getName();
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        SubjectDto selectedSubject = null;
        if (subjectId != null) {
            selectedSubject = subjectService.getSubjectById(subjectId, userEmail);
        }

        model.addAttribute("subjects", subjects);
        model.addAttribute("selectedSubject", selectedSubject);
        model.addAttribute("taskDto", new TaskDto(
                null,
                subjectId,
                selectedSubject != null ? selectedSubject.name() : null,
                "",
                "",
                com.studyflow.studyflow.entity.TaskType.ASSIGNMENT,
                LocalDate.now().plusDays(1),
                null,
                com.studyflow.studyflow.entity.RecurrenceType.ONCE,
                0,
                false
        ));

        return "task/add-task";
    }

    /**
     * Save a new Task, assigned to the subject (owned by the authenticated user).
     */
    @PostMapping("/save")
    public String saveTask(
            @Valid @ModelAttribute("taskDto") TaskDto taskDto,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        String userEmail = authentication.getName();
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        // If validation fails, return to the form with subjects
        if (bindingResult.hasErrors()) {
            SubjectDto selectedSubject = null;
            if (taskDto.subjectId() != null) {
                selectedSubject = subjectService.getSubjectById(taskDto.subjectId(), userEmail);
            }
            model.addAttribute("subjects", subjects);
            model.addAttribute("selectedSubject", selectedSubject);
            return "task/add-task";
        }

        taskService.saveTask(taskDto, taskDto.subjectId(), userEmail);

        redirectAttributes.addFlashAttribute("toastMessage", "Task created successfully.");
        redirectAttributes.addFlashAttribute("toastType", "success");

        return "redirect:/tasks";
    }

    /**
     * Display Edit Task form for a task owned by the authenticated user.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Authentication authentication,
            Model model
    ) {

        String userEmail = authentication.getName();
        TaskDto taskDto = taskService.getTaskById(id, userEmail);
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        model.addAttribute("taskDto", taskDto);
        model.addAttribute("subjects", subjects);

        return "task/edit-task";
    }

    /**
     * Update an existing Task, verifying ownership first.
     */
    @PostMapping("/update/{id}")
    public String updateTask(
            @PathVariable Long id,
            @Valid @ModelAttribute("taskDto") TaskDto taskDto,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {

        String userEmail = authentication.getName();
        List<SubjectDto> subjects = subjectService.getAllSubjects(userEmail);

        if (bindingResult.hasErrors()) {
            model.addAttribute("subjects", subjects);
            return "task/edit-task";
        }

        taskService.updateTask(id, taskDto, userEmail);

        redirectAttributes.addFlashAttribute("toastMessage", "Task updated successfully.");
        redirectAttributes.addFlashAttribute("toastType", "success");

        return "redirect:/tasks";
    }

    /**
     * Update task progress (AJAX endpoint).
     */
    @PostMapping("/{id}/progress")
    @ResponseBody
    public TaskDto updateProgress(
            @PathVariable Long id,
            @RequestParam Integer progress,
            Authentication authentication
    ) {

        return taskService.updateTaskProgress(id, progress, authentication.getName());
    }

    /**
     * Mark task as complete.
     */
    @PostMapping("/{id}/complete")
    @ResponseBody
    public TaskDto completeTask(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        TaskDto completedTask = taskService.completeTask(id, authentication.getName());

        redirectAttributes.addFlashAttribute("toastMessage", "Task completed successfully!");
        redirectAttributes.addFlashAttribute("toastType", "success");

        return completedTask;
    }

    /**
     * Delete a task owned by the authenticated user.
     */
    @GetMapping("/delete/{id}")
    public String deleteTask(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {

        taskService.deleteTask(id, authentication.getName());

        redirectAttributes.addFlashAttribute("toastMessage", "Task deleted successfully.");
        redirectAttributes.addFlashAttribute("toastType", "success");

        return "redirect:/tasks";
    }
}