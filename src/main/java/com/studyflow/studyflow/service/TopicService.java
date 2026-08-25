package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.TopicDto;

import java.util.List;

/**
 * Service interface for Topic operations.
 *
 * Every operation is ownership-aware: it takes the authenticated user's
 * email and the implementation resolves that user, then queries only the
 * topics the user owns (via their subjects). The owner is derived from the
 * authentication context, never from the request payload.
 */
public interface TopicService {

    /**
     * Create a new topic and assign it to the given subject (owned by the user).
     *
     * @param topicDto   topic data received from the form
     * @param subjectId  the subject this topic belongs to
     * @param userEmail  the authenticated user's email
     * @return saved topic information
     */
    TopicDto saveTopic(TopicDto topicDto, Long subjectId, String userEmail);


    /**
     * Retrieve all topics owned by the given user (via their subjects).
     *
     * @param userEmail the authenticated user's email
     * @return the user's topics only
     */
    List<TopicDto> getAllTopics(String userEmail);


    /**
     * Retrieve all topics belonging to a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @return the subject's topics
     */
    List<TopicDto> getTopicsBySubject(Long subjectId, String userEmail);


    /**
     * Find a topic belonging to the given user (via subject ownership).
     *
     * @param id        topic primary key
     * @param userEmail the authenticated user's email
     * @return topic information if it belongs to the user
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the topic does not belong to the user (or does not exist)
     */
    TopicDto getTopicById(Long id, String userEmail);


    /**
     * Update a topic belonging to the given user.
     *
     * Ownership is verified before any change is applied.
     *
     * @param id         existing topic id
     * @param topicDto   updated topic information
     * @param userEmail  the authenticated user's email
     * @return updated topic information
     */
    TopicDto updateTopic(Long id, TopicDto topicDto, String userEmail);


    /**
     * Delete a topic belonging to the given user.
     *
     * Ownership is verified before the destructive operation.
     *
     * @param id        topic primary key
     * @param userEmail the authenticated user's email
     */
    void deleteTopic(Long id, String userEmail);

}