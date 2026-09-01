package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Task entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used by user-facing pages is scoped to the owning
 * {@link User} via their {@link Subject} so that one user can never
 * observe another user's tasks (IDOR prevention).
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * All tasks belonging to subjects owned by the given user,
     * ordered by due date then id for stable output.
     *
     * @param user the owning user
     * @return the user's tasks
     */
    List<Task> findBySubject_UserOrderByDueDateAscIdAsc(User user);

    /**
     * Find a task by id only when it belongs to a subject owned by the given user.
     *
     * @param id   task primary key
     * @param user the owning user (via subject)
     * @return the task when owned by the user, empty otherwise
     */
    Optional<Task> findByIdAndSubject_User(Long id, User user);

    /**
     * All tasks belonging to a specific subject.
     *
     * @param subject the subject
     * @return the subject's tasks
     */
    List<Task> findBySubjectOrderByDueDateAscIdAsc(Subject subject);

    /**
     * Find a task by id within a specific subject.
     *
     * @param id      task primary key
     * @param subject the subject
     * @return the task when it belongs to the subject, empty otherwise
     */
    Optional<Task> findByIdAndSubject(Long id, Subject subject);

    /**
     * All upcoming tasks (due date >= today) for the given user.
     *
     * @param user the owning user
     * @param today the current date
     * @return the user's upcoming tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate >= :today ORDER BY t.dueDate ASC, t.dueTime ASC, t.id ASC")
    List<Task> findUpcomingByUser(User user, LocalDate today);

    /**
     * All past tasks (due date < today) for the given user.
     *
     * @param user the owning user
     * @param today the current date
     * @return the user's past tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate < :today ORDER BY t.dueDate DESC, t.dueTime DESC, t.id DESC")
    List<Task> findPastByUser(User user, LocalDate today);

    /**
     * Upcoming tasks for a specific subject.
     *
     * @param subject the subject
     * @param today the current date
     * @return the subject's upcoming tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject = :subject AND t.dueDate >= :today ORDER BY t.dueDate ASC, t.dueTime ASC, t.id ASC")
    List<Task> findUpcomingBySubject(Subject subject, LocalDate today);

    /**
     * Past tasks for a specific subject.
     *
     * @param subject the subject
     * @param today the current date
     * @return the subject's past tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject = :subject AND t.dueDate < :today ORDER BY t.dueDate DESC, t.dueTime DESC, t.id DESC")
    List<Task> findPastBySubject(Subject subject, LocalDate today);

    /**
     * Search tasks by title for a specific user.
     *
     * @param user the owning user
     * @param searchTerm the search term (case-insensitive)
     * @return matching tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND LOWER(t.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY t.dueDate ASC, t.id ASC")
    List<Task> searchByUser(User user, String searchTerm);

    /**
     * Search tasks by title for a specific subject.
     *
     * @param subject the subject
     * @param searchTerm the search term (case-insensitive)
     * @return matching tasks
     */
    @Query("SELECT t FROM Task t WHERE t.subject = :subject AND LOWER(t.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY t.dueDate ASC, t.id ASC")
    List<Task> searchBySubject(Subject subject, String searchTerm);

    /**
     * Count the tasks belonging to subjects owned by the given user.
     *
     * @param user the owning user
     * @return the number of tasks the user owns
     */
    long countBySubject_User(User user);

    /**
     * Count the tasks belonging to a specific subject.
     *
     * @param subject the subject
     * @return the number of tasks in the subject
     */
    long countBySubject(Subject subject);

    /**
     * The user's tasks due today for the dashboard: incomplete tasks first,
     * then by due time then id for a stable, actionable order. Scoped to the
     * owning user via the subject chain.
     *
     * @param user  the owning user
     * @param today the current date (tasks due on this date)
     * @return the user's today tasks, incomplete first
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate = :today " +
            "ORDER BY t.completed ASC, t.dueTime ASC, t.id ASC")
    List<Task> findDueTodayByUser(User user, LocalDate today);

    /**
     * The user's nearest upcoming incomplete tasks (due strictly after today)
     * for the dashboard, soonest first. Scoped to the owning user via the
     * subject chain. Completed and past tasks are excluded.
     *
     * @param user  the owning user
     * @param today the current date
     * @return the user's upcoming incomplete tasks, soonest first
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate > :today " +
            "AND t.completed = false ORDER BY t.dueDate ASC, t.dueTime ASC, t.id ASC")
    List<Task> findUpcomingIncompleteByUser(User user, LocalDate today);

    /**
     * The user's incomplete tasks due today, soonest time first. Used by the
     * notification generation service for the Task Due rule; completed tasks
     * (which must never trigger a due/overdue notification) are excluded.
     * Scoped to the owning user via the subject chain.
     *
     * @param user  the owning user
     * @param today the current date (tasks due on this date)
     * @return the user's incomplete tasks due today
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate = :today " +
            "AND t.completed = false ORDER BY t.dueTime ASC, t.id ASC")
    List<Task> findIncompleteDueTodayByUser(User user, LocalDate today);

    /**
     * The user's incomplete, overdue tasks (due before today). Used by the
     * notification generation service for the Task Overdue rule; completed
     * tasks are excluded. Scoped to the owning user via the subject chain.
     *
     * @param user  the owning user
     * @param today the current date (overdue means due before this date)
     * @return the user's incomplete overdue tasks, most-overdue first
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate < :today " +
            "AND t.completed = false ORDER BY t.dueDate ASC, t.dueTime ASC, t.id ASC")
    List<Task> findIncompleteOverdueByUser(User user, LocalDate today);

    /**
     * Tasks within a date range (inclusive) belonging to subjects owned by
     * the given user. Used by the calendar to fetch all tasks whose due date
     * falls inside the displayed period.
     *
     * @param user  the owning user
     * @param start first date of the range (inclusive)
     * @param end   last date of the range (inclusive)
     * @return the user's tasks in the range, soonest first
     */
    @Query("SELECT t FROM Task t WHERE t.subject.user = :user AND t.dueDate BETWEEN :start AND :end " +
            "ORDER BY t.dueDate ASC, t.dueTime ASC, t.id ASC")
    List<Task> findBySubject_UserAndDueDateBetween(User user, LocalDate start, LocalDate end);

    /**
     * Delete all tasks belonging to a specific subject.
     * Used when deleting a subject to maintain referential integrity.
     *
     * @param subject the subject whose tasks should be deleted
     */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.subject = :subject")
    void deleteBySubject(Subject subject);
}