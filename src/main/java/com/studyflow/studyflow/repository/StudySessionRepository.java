package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /**
     * Total study minutes for the user between the two dates (inclusive).
     * Aggregation happens in the database, scoped to the owning user via
     * the subject chain, so the dashboard never loads or filters rows in
     * memory. Returns 0 (not null) when the user has no sessions.
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return the summed duration in minutes, 0 when empty
     */
    @Query("select coalesce(sum(s.durationMinutes), 0L) from StudySession s " +
            "where s.subject.user = :user and s.sessionDate between :start and :end")
    Long sumDurationMinutesBySubject_UserAndSessionDateBetween(User user, LocalDate start, LocalDate end);

    /**
     * Total study minutes ever recorded for the user.
     * Aggregation happens in the database, scoped to the owning user.
     *
     * @param user the owning user
     * @return the all-time summed duration in minutes, 0 when empty
     */
    @Query("select coalesce(sum(s.durationMinutes), 0L) from StudySession s " +
            "where s.subject.user = :user")
    Long sumDurationMinutesBySubject_User(User user);

    /**
     * Number of study sessions the user logged between the two dates (inclusive).
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return the session count, 0 when empty
     */
    long countBySubject_UserAndSessionDateBetween(User user, LocalDate start, LocalDate end);

    /**
     * Number of study sessions ever recorded for the user.
     *
     * @param user the owning user
     * @return the all-time session count, 0 when empty
     */
    long countBySubject_User(User user);

    /**
     * The distinct calendar dates on which the user recorded at least one
     * study session with a positive duration, in no particular order.
     *
     * Streaks are computed from active dates (not session count): a day with
     * several sessions counts once. Only positive-duration sessions mark a
     * day as active, matching the streak definition.
     *
     * @param user the owning user
     * @return the distinct active dates, empty when there is no activity
     */
    @Query("select distinct s.sessionDate from StudySession s " +
            "where s.subject.user = :user and s.durationMinutes > 0")
    List<LocalDate> findDistinctActiveDatesBySubject_User(User user);

    /**
     * The five newest study sessions owned by the user, newest first.
     * Same ordering as the full history query, capped for the dashboard.
     *
     * @param user the owning user
     * @return up to five most recent sessions, newest first
     */
    List<StudySession> findTop5BySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(User user);

    /**
     * Total study minutes per subject for the user, only for subjects that
     * have at least one logged session. Returns rows of
     * [subject.id, subject.name, subject.color, totalMinutes].
     *
     * @param user the owning user
     * @return per-subject totals, one row per subject with activity
     */
    @Query("select s.subject.id, s.subject.name, s.subject.color, coalesce(sum(s.durationMinutes), 0L) " +
            "from StudySession s " +
            "where s.subject.user = :user " +
            "group by s.subject.id, s.subject.name, s.subject.color")
    List<Object[]> sumDurationMinutesBySubjectForUser(User user);

    /**
     * Total study minutes per date for the user between the two dates
     * (inclusive). Returns rows of [sessionDate, totalMinutes].
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return per-date totals, one row per date with activity
     */
    @Query("select s.sessionDate, coalesce(sum(s.durationMinutes), 0L) " +
            "from StudySession s " +
            "where s.subject.user = :user and s.sessionDate between :start and :end " +
            "group by s.sessionDate")
    List<Object[]> sumDurationMinutesByDateForUser(User user, LocalDate start, LocalDate end);

    /**
     * Total study minutes per session type for the user. Returns rows of
     * [StudySessionType, totalMinutes], one row per type with activity.
     *
     * @param user the owning user
     * @return per-type totals, one row per type with activity
     */
    @Query("select s.sessionType, coalesce(sum(s.durationMinutes), 0L) " +
            "from StudySession s " +
            "where s.subject.user = :user " +
            "group by s.sessionType")
    List<Object[]> sumDurationMinutesByTypeForUser(User user);

    /**
     * Study sessions within a date range (inclusive) belonging to subjects
     * owned by the given user. Used by the calendar to fetch all sessions
     * whose session date falls inside the displayed period.
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return the user's study sessions in the range, soonest first
     */
    @Query("SELECT s FROM StudySession s WHERE s.subject.user = :user AND s.sessionDate BETWEEN :start AND :end " +
            "ORDER BY s.sessionDate ASC, s.startTime ASC, s.id ASC")
    List<StudySession> findBySubject_UserAndSessionDateBetween(User user, LocalDate start, LocalDate end);
}