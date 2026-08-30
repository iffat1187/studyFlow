package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for StudySession entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used by user-facing pages is scoped to the owning
 * {@link User} via their {@link Subject} so that one user can never
 * observe another user's study sessions (IDOR prevention).
 *
 * Ownership chain: StudySession → Subject → User
 */
@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /**
     * All study sessions belonging to subjects owned by the given user,
     * newest first: session date, then start time, then id, all descending
     * for stable output.
     *
     * @param user the owning user
     * @return the user's study sessions, newest first
     */
    List<StudySession> findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(User user);

    /**
     * Find a study session by id only when it belongs to a subject owned by the given user.
     *
     * @param id   study session primary key
     * @param user the owning user (via subject)
     * @return the session when owned by the user, empty otherwise
     */
    Optional<StudySession> findByIdAndSubject_User(Long id, User user);

    /**
     * All study sessions belonging to a specific subject.
     *
     * @param subject the subject
     * @return the subject's study sessions
     */
    List<StudySession> findBySubjectOrderBySessionDateAscStartTimeAscIdAsc(Subject subject);
}