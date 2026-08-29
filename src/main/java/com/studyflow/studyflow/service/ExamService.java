package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.entity.ExamType;

import java.util.List;

/**
 * Service interface for Exam operations.
 *
 * Every operation is ownership-aware: it takes the authenticated user's
 * email and the implementation resolves that user, then queries only the
 * exams the user owns (via their subjects). The owner is derived from the
 * authentication context, never from the request payload.
 */
public interface ExamService {

    /**
     * Create a new exam and assign it to the given subject (owned by the user).
     *
     * @param examDto   exam data received from the form
     * @param subjectId the subject this exam belongs to
     * @param userEmail the authenticated user's email
     * @return saved exam information
     */
    ExamDto saveExam(ExamDto examDto, Long subjectId, String userEmail);


    /**
     * Retrieve all exams owned by the given user (via their subjects).
     *
     * @param userEmail the authenticated user's email
     * @return the user's exams only
     */
    List<ExamDto> getAllExams(String userEmail);


    /**
     * Retrieve the user's exams (upcoming or past) with optional filters.
     *
     * Filtering happens in the database, scoped to the authenticated user,
     * so the visible exams always belong to that user. An empty/blank search
     * term and a null subject id/type are treated as "no filter".
     *
     * @param userEmail the authenticated user's email
     * @param search    optional exam-name search fragment (may be null/blank)
     * @param subjectId optional subject filter (may be null)
     * @param examType  optional exam type filter (may be null)
     * @param upcoming  true for today-or-future exams, false for past exams
     * @return the user's matching exams only
     */
    List<ExamDto> getExams(String userEmail, String search, Long subjectId, ExamType examType, boolean upcoming);


    /**
     * Retrieve all exams belonging to a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @return the subject's exams
     */
    List<ExamDto> getExamsBySubject(Long subjectId, String userEmail);


    /**
     * Find an exam belonging to the given user (via subject ownership).
     *
     * @param id        exam primary key
     * @param userEmail the authenticated user's email
     * @return exam information if it belongs to the user
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the exam does not belong to the user (or does not exist)
     */
    ExamDto getExamById(Long id, String userEmail);


    /**
     * Update an exam belonging to the given user.
     *
     * Ownership is verified before any change is applied. The exam may be
     * moved to a different subject; when it is, the target subject (taken
     * from {@code examDto.subjectId()}) must also belong to the user,
     * otherwise the update is rejected.
     *
     * @param id        existing exam id
     * @param examDto   updated exam information (subjectId = target subject)
     * @param userEmail the authenticated user's email
     * @return updated exam information
     */
    ExamDto updateExam(Long id, ExamDto examDto, String userEmail);


    /**
     * Delete an exam belonging to the given user.
     *
     * Ownership is verified before the destructive operation.
     *
     * @param id        exam primary key
     * @param userEmail the authenticated user's email
     */
    void deleteExam(Long id, String userEmail);


    /**
     * True when the user owns at least one exam.
     *
     * Used by the dashboard setup checklist; resolved with a database-side
     * existence check so no exam rows are loaded into memory.
     *
     * @param userEmail the authenticated user's email
     * @return true when the user has at least one exam
     */
    boolean userHasAnyExam(String userEmail);

}