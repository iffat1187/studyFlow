package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.StudySessionDto;

import java.util.List;

/**
 * Service interface for StudySession operations.
 *
 * Every operation is ownership-aware: it takes the authenticated user's
 * email and the implementation resolves that user, then queries only the
 * study sessions the user owns (via their subjects). The owner is derived
 * from the authentication context, never from the request payload.
 *
 * Ownership chain: Authenticated User → Subject → (optional Topic) →
 * StudySession.
 */
public interface StudySessionService {

    /**
     * Create a new study session for a subject owned by the user.
     *
     * The submitted subjectId and optional topicId are validated: the
     * subject must belong to the user, and when a topic is supplied it must
     * belong to that same subject (and therefore to the user).
     *
     * @param studySessionDto study session data received from the form
     * @param userEmail       the authenticated user's email
     * @return saved study session information
     */
    StudySessionDto saveSession(StudySessionDto studySessionDto, String userEmail);


    /**
     * Retrieve all study sessions owned by the given user (via their subjects),
     * newest first.
     *
     * @param userEmail the authenticated user's email
     * @return the user's study sessions only
     */
    List<StudySessionDto> getAllSessions(String userEmail);


    /**
     * Find a study session belonging to the given user (via subject ownership).
     *
     * @param id        study session primary key
     * @param userEmail the authenticated user's email
     * @return session information if it belongs to the user
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the session does not belong to the user (or does not exist)
     */
    StudySessionDto getSessionById(Long id, String userEmail);


    /**
     * Update a study session belonging to the given user.
     *
     * Ownership of the session, the selected subject and any selected topic
     * is verified before any change is applied. The session may be moved to
     * a different subject; when it is, the new subject must also belong to
     * the user and the optional topic must belong to that subject.
     *
     * @param id              existing study session id
     * @param studySessionDto updated study session information
     * @param userEmail       the authenticated user's email
     * @return updated study session information
     */
    StudySessionDto updateSession(Long id, StudySessionDto studySessionDto, String userEmail);


    /**
     * Delete a study session belonging to the given user.
     *
     * Ownership is verified before the destructive operation.
     *
     * @param id        study session primary key
     * @param userEmail the authenticated user's email
     */
    void deleteSession(Long id, String userEmail);

}