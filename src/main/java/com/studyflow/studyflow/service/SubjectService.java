package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.SubjectDto;

import java.util.List;

/**
 * Service interface for Subject operations.
 *
 * Defines all business operations related to subjects.
 * The implementation will be provided by SubjectServiceImpl.
 */
public interface SubjectService {

    /**
     * Create a new subject.
     *
     * @param subjectDto subject data received from user
     * @return saved subject information
     */
    SubjectDto saveSubject(SubjectDto subjectDto);


    /**
     * Retrieve all subjects.
     *
     * @return list of all subjects
     */
    List<SubjectDto> getAllSubjects();


    /**
     * Find a subject by its ID.
     *
     * @param id subject primary key
     * @return subject information
     */
    SubjectDto getSubjectById(Long id);


    /**
     * Update an existing subject.
     *
     * @param id existing subject id
     * @param subjectDto updated subject information
     * @return updated subject information
     */
    SubjectDto updateSubject(Long id, SubjectDto subjectDto);


    /**
     * Delete a subject by ID.
     *
     * @param id subject primary key
     */
    void deleteSubject(Long id);

}