package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.TopicDto;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of TopicService.
 *
 * Contains the business logic for Topic CRUD operations. Every operation
 * is scoped to the authenticated user resolved from the provided email:
 * topics are created for subjects owned by that user and all reads/updates/deletes
 * are backed by user-scoped repository lookups, which prevents one user from
 * accessing another user's topics (IDOR).
 */
@Service
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the repositories.
     */
    public TopicServiceImpl(TopicRepository topicRepository,
                            SubjectRepository subjectRepository,
                            UserRepository userRepository) {
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a new topic and associate it with the subject (owned by the user).
     */
    @Override
    @Transactional
    public TopicDto saveTopic(TopicDto topicDto, Long subjectId, String userEmail) {

        User user = requireUser(userEmail);
        Subject subject = requireOwnedSubject(subjectId, userEmail);

        // Convert DTO to Entity and assign the subject from the security context.
        Topic topic = new Topic();
        topic.setName(topicDto.name());
        topic.setDescription(topicDto.description());
        topic.setSubject(subject);

        // Save Entity
        Topic savedTopic = topicRepository.save(topic);

        // Convert Entity back to DTO
        return new TopicDto(
                savedTopic.getId(),
                savedTopic.getSubject().getId(),
                savedTopic.getSubject().getName(),
                savedTopic.getName(),
                savedTopic.getDescription()
        );
    }

    /**
     * Retrieve all topics belonging to the authenticated user (via their subjects).
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> getAllTopics(String userEmail) {

        User user = requireUser(userEmail);

        return topicRepository.findBySubject_UserOrderByIdAsc(user)
                .stream()
                .map(topic -> new TopicDto(
                        topic.getId(),
                        topic.getSubject().getId(),
                        topic.getSubject().getName(),
                        topic.getName(),
                        topic.getDescription()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all topics belonging to a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> getTopicsBySubject(Long subjectId, String userEmail) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);

        return topicRepository.findBySubjectOrderByIdAsc(subject)
                .stream()
                .map(topic -> new TopicDto(
                        topic.getId(),
                        topic.getSubject().getId(),
                        topic.getSubject().getName(),
                        topic.getName(),
                        topic.getDescription()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a topic by ID, only when it belongs to a subject owned by the authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public TopicDto getTopicById(Long id, String userEmail) {

        Topic topic = requireOwnedTopic(id, userEmail);

        return new TopicDto(
                topic.getId(),
                topic.getSubject().getId(),
                topic.getSubject().getName(),
                topic.getName(),
                topic.getDescription()
        );
    }

    /**
     * Update an existing topic, after verifying ownership (via subject).
     */
    @Override
    @Transactional
    public TopicDto updateTopic(Long id, TopicDto topicDto, String userEmail) {

        Topic topic = requireOwnedTopic(id, userEmail);

        topic.setName(topicDto.name());
        topic.setDescription(topicDto.description());

        Topic updatedTopic = topicRepository.save(topic);

        return new TopicDto(
                updatedTopic.getId(),
                updatedTopic.getSubject().getId(),
                updatedTopic.getSubject().getName(),
                updatedTopic.getName(),
                updatedTopic.getDescription()
        );
    }

    /**
     * Delete a topic, only after verifying ownership (via subject).
     */
    @Override
    @Transactional
    public void deleteTopic(Long id, String userEmail) {

        Topic topic = requireOwnedTopic(id, userEmail);

        topicRepository.delete(topic);
    }

    /**
     * Resolve the authenticated user from the security context email.
     */
    private User requireUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for the authenticated email"));
    }

    /**
     * Ownership gate: the subject must exist AND belong to the user.
     *
     * @throws AccessDeniedException when the subject is not owned by the user
     */
    private Subject requireOwnedSubject(Long subjectId, String userEmail) {
        User user = requireUser(userEmail);
        return subjectRepository.findByIdAndUser(subjectId, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Subject not found for this user"));
    }

    /**
     * Ownership gate: the topic must exist AND belong to a subject owned by the user.
     *
     * A single lookup avoids leaking whether the id exists at all.
     *
     * @throws AccessDeniedException when the topic is not owned by the user
     */
    private Topic requireOwnedTopic(Long id, String userEmail) {
        User user = requireUser(userEmail);
        return topicRepository.findByIdAndSubject_User(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Topic not found for this user"));
    }
}