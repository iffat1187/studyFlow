package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.SubjectDto;

import java.util.List;

/**
 * Service interface for Subject operations.
 *
 * Every operation is ownership-aware: it takes the authenticated user's
 * email and the implementation resolves that user, then queries only the
 * subjects the user owns. The owner is derived from the authentication
 * context, never from the request payload.
 */
public interface SubjectService {

    /**
     * Create a new subject and assign it to the given user.
     *
     * @param subjectDto subject data received from the form
     * @param userEmail  the authenticated user's email
     * @return saved subject information
     */
    SubjectDto saveSubject(SubjectDto subjectDto, String userEmail);


    /**
     * Retrieve all subjects owned by the given user.
     *
     * @param userEmail the authenticated user's email
     * @return the user's subjects only
     */
    List<SubjectDto> getAllSubjects(String userEmail);


    /**
     * Find a subject belonging to the given user.
     *
     * @param id        subject primary key
     * @param userEmail the authenticated user's email
     * @return subject information if it belongs to the user
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the subject does not belong to the user (or does not exist)
     */
    SubjectDto getSubjectById(Long id, String userEmail);


    /**
     * Update a subject belonging to the given user.
     *
     * Ownership is verified before any change is applied.
     *
     * @param id         existing subject id
     * @param subjectDto updated subject information
     * @param userEmail  the authenticated user's email
     * @return updated subject information
     */
    SubjectDto updateSubject(Long id, SubjectDto subjectDto, String userEmail);


    /**
     * Delete a subject belonging to the given user.
     *
     * Ownership is verified before the destructive operation.
     *
     * @param id        subject primary key
     * @param userEmail the authenticated user's email
     */
    void deleteSubject(Long id, String userEmail);

}