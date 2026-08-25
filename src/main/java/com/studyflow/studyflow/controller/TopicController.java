package com.studyflow.studyflow.controller;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.dto.TopicDto;
import com.studyflow.studyflow.service.SubjectService;
import com.studyflow.studyflow.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller responsible for handling all Topic related requests.
 *
 * The controller stays thin: it resolves the authenticated user's email
 * from the security context and delegates every ownership-aware operation
 * to {@link TopicService}. No user identifier is ever read from the
 * request payload or URL.
 */
@Controller
@RequestMapping("/topics")
public class TopicController {

    private final TopicService topicService;
    private final SubjectService subjectService;

    /**
     * Constructor Injection.
     */
    public TopicController(TopicService topicService, SubjectService subjectService) {
        this.topicService = topicService;
        this.subjectService = subjectService;
    }

    /**
     * Display the topics owned by the authenticated user.
     */
    @GetMapping
    public String listTopics(Authentication authentication, Model model) {

        model.addAttribute("topics",
                topicService.getAllTopics(authentication.getName()));

        return "topic/list-topic";
    }

    /**
     * Display topics for a specific subject.
     */
    @GetMapping("/subject/{subjectId}")
    public String listTopicsBySubject(
            @PathVariable Long subjectId,
            Authentication authentication,
            Model model
    ) {

        // Get subject info for display
        SubjectDto subjectDto = subjectService.getSubjectById(subjectId, authentication.getName());

        model.addAttribute("topics",
                topicService.getTopicsBySubject(subjectId, authentication.getName()));
        model.addAttribute("subject", subjectDto);

        return "topic/list-topic";
    }

    /**
     * Display Add Topic form for a specific subject.
     */
    @GetMapping("/subject/{subjectId}/new")
    public String showAddForm(
            @PathVariable Long subjectId,
            Authentication authentication,
            Model model
    ) {

        // Verify subject ownership before showing form
        var subjectDto = subjectService.getSubjectById(subjectId, authentication.getName());

        model.addAttribute("topicDto", new TopicDto(
                null,
                subjectId,
                subjectDto.name(),
                "",
                ""
        ));

        return "topic/add-topic";
    }

    /**
     * Save a new Topic, assigned to the subject (owned by the authenticated user).
     */
    @PostMapping("/subject/{subjectId}/save")
    public String saveTopic(
            @PathVariable Long subjectId,
            @Valid @ModelAttribute("topicDto") TopicDto topicDto,
            BindingResult bindingResult,
            Authentication authentication
    ) {

        // If validation fails, return to the form.
        if (bindingResult.hasErrors()) {
            return "topic/add-topic";
        }

        topicService.saveTopic(topicDto, subjectId, authentication.getName());

        return "redirect:/topics/subject/" + subjectId;
    }

    /**
     * Display Edit Topic form for a topic owned by the authenticated user.
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(
            @PathVariable Long id,
            Model model,
            Authentication authentication
    ) {

        TopicDto topicDto = topicService.getTopicById(id, authentication.getName());
        model.addAttribute("topicDto", topicDto);

        return "topic/edit-topic";
    }

    /**
     * Update an existing Topic, verifying ownership first.
     */
    @PostMapping("/update/{id}")
    public String updateTopic(
            @PathVariable Long id,
            @Valid @ModelAttribute("topicDto") TopicDto topicDto,
            BindingResult bindingResult,
            Authentication authentication
    ) {

        if (bindingResult.hasErrors()) {
            return "topic/edit-topic";
        }

        // Get the topic first to know its subjectId for redirect
        TopicDto existingTopic = topicService.getTopicById(id, authentication.getName());

        topicService.updateTopic(id, topicDto, authentication.getName());

        return "redirect:/topics/subject/" + existingTopic.subjectId();
    }

    /**
     * Delete a topic owned by the authenticated user.
     */
    @GetMapping("/delete/{id}")
    public String deleteTopic(
            @PathVariable Long id,
            Authentication authentication
    ) {

        // Get the topic first to know its subjectId for redirect
        TopicDto existingTopic = topicService.getTopicById(id, authentication.getName());

        topicService.deleteTopic(id, authentication.getName());

        return "redirect:/topics/subject/" + existingTopic.subjectId();
    }

}