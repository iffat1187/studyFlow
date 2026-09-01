package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Exam entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used by user-facing pages is scoped to the owning
 * {@link User} via their {@link Subject} so that one user can never
 * observe another user's exams (IDOR prevention).
 */
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    /**
     * All exams belonging to subjects owned by the given user,
     * ordered by exam date, then time, then id for stable output.
     *
     * @param user the owning user
     * @return the user's exams
     */
    List<Exam> findBySubject_UserOrderByExamDateAscExamTimeAscIdAsc(User user);

    /**
     * Find an exam by id only when it belongs to a subject owned by the given user.
     *
     * @param id   exam primary key
     * @param user the owning user (via subject)
     * @return the exam when owned by the user, empty otherwise
     */
    Optional<Exam> findByIdAndSubject_User(Long id, User user);

    /**
     * All exams belonging to a specific subject.
     *
     * @param subject the subject
     * @return the subject's exams
     */
    List<Exam> findBySubjectOrderByExamDateAscExamTimeAscIdAsc(Subject subject);

    /**
     * Find an exam by id within a specific subject.
     *
     * @param id      exam primary key
     * @param subject the subject
     * @return the exam when it belongs to the subject, empty otherwise
     */
    Optional<Exam> findByIdAndSubject(Long id, Subject subject);

    /**
     * Count the exams belonging to subjects owned by the given user.
     *
     * @param user the owning user
     * @return the number of exams the user owns
     */
    long countBySubject_User(User user);

    /**
     * Count the exams belonging to a specific subject.
     *
     * @param subject the subject
     * @return the number of exams in the subject
     */
    long countBySubject(Subject subject);

    /**
     * True when the user owns at least one exam across their subjects.
     *
     * Existence checks are resolved in the database without loading any rows,
     * so the dashboard setup checklist can stay efficient (no N+1, no
     * unnecessary entity materialization).
     *
     * @param user the owning user
     * @return true when the user owns one or more exams
     */
    boolean existsBySubject_User(User user);

    /**
     * Delete all exams belonging to a specific subject.
     * Used when deleting a subject to maintain referential integrity.
     *
     * @param subject the subject whose exams should be deleted
     */
    @Modifying
    @Query("DELETE FROM Exam e WHERE e.subject = :subject")
    void deleteBySubject(Subject subject);

    /**
     * Upcoming exams (on or after the given day) belonging to subjects owned by the user,
     * filtered by an optional case-insensitive name search, subject id and exam type.
     *
     * Null/missing filters are simply ignored, so any combination of
     * search + subject + type works together while the result set stays
     * scoped to the authenticated user (IDOR prevention).
     *
     * @param user      the owning user
     * @param today     lower bound (inclusive) for upcoming exams
     * @param search    optional exam-name search fragment (may be null)
     * @param subjectId optional subject filter (may be null)
     * @param examType  optional exam type filter (may be null)
     * @return matching exams, soonest first
     */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.subject.user = :user
              AND e.examDate >= :today
              AND (:search IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:subjectId IS NULL OR e.subject.id = :subjectId)
              AND (:examType IS NULL OR e.examType = :examType)
            ORDER BY e.examDate ASC, e.examTime ASC, e.id ASC
            """)
    List<Exam> findUpcoming(@Param("user") User user,
                            @Param("today") LocalDate today,
                            @Param("search") String search,
                            @Param("subjectId") Long subjectId,
                            @Param("examType") ExamType examType);

    /**
     * Past exams (strictly before the given day), same optional filters as
     * {@link #findUpcoming(User, LocalDate, String, Long, ExamType)}.
     *
     * @param user      the owning user
     * @param today     upper bound (exclusive) for past exams
     * @param search    optional exam-name search fragment (may be null)
     * @param subjectId optional subject filter (may be null)
     * @param examType  optional exam type filter (may be null)
     * @return matching exams, most recent first
     */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.subject.user = :user
              AND e.examDate < :today
              AND (:search IS NULL OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:subjectId IS NULL OR e.subject.id = :subjectId)
              AND (:examType IS NULL OR e.examType = :examType)
            ORDER BY e.examDate DESC, e.examTime DESC, e.id DESC
            """)
    List<Exam> findPast(@Param("user") User user,
                        @Param("today") LocalDate today,
                        @Param("search") String search,
                        @Param("subjectId") Long subjectId,
                        @Param("examType") ExamType examType);

    /**
     * Exams on or after the given day belonging to subjects owned by the
     * given user. Used by the notification generation service for the Exam
     * Today and Exam Tomorrow rules; past exams are excluded so rules never
     * fire on exams that have already happened.
     *
     * @param user      the owning user
     * @param dateFrom  lower bound (inclusive) for the exam date
     * @return the user's exams on or after the given date, soonest first
     */
    @Query("SELECT e FROM Exam e WHERE e.subject.user = :user AND e.examDate >= :dateFrom " +
            "ORDER BY e.examDate ASC, e.examTime ASC, e.id ASC")
    List<Exam> findUpcomingFromByUser(@Param("user") User user, @Param("dateFrom") LocalDate dateFrom);

    /**
     * Exams within a date range (inclusive) belonging to subjects owned by
     * the given user. Used by the calendar to fetch all exams whose exam date
     * falls inside the displayed period.
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return the user's exams in the range, soonest first
     */
    @Query("SELECT e FROM Exam e WHERE e.subject.user = :user AND e.examDate BETWEEN :start AND :end " +
            "ORDER BY e.examDate ASC, e.examTime ASC, e.id ASC")
    List<Exam> findBySubject_UserAndExamDateBetween(User user, LocalDate start, LocalDate end);
}