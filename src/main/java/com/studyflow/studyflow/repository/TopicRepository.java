package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Topic entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used by user-facing pages is scoped to the owning
 * {@link User} via their {@link Subject} so that one user can never
 * observe another user's topics (IDOR prevention).
 */
@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    /**
     * All topics belonging to subjects owned by the given user,
     * ordered by id for stable output.
     *
     * @param user the owning user
     * @return the user's topics
     */
    List<Topic> findBySubject_UserOrderByIdAsc(User user);

    /**
     * Find a topic by id only when it belongs to a subject owned by the given user.
     *
     * @param id   topic primary key
     * @param user the owning user (via subject)
     * @return the topic when owned by the user, empty otherwise
     */
    Optional<Topic> findByIdAndSubject_User(Long id, User user);

    /**
     * All topics belonging to a specific subject.
     *
     * @param subject the subject
     * @return the subject's topics
     */
    List<Topic> findBySubjectOrderByIdAsc(Subject subject);

    /**
     * Find a topic by id within a specific subject.
     *
     * @param id      topic primary key
     * @param subject the subject
     * @return the topic when it belongs to the subject, empty otherwise
     */
    Optional<Topic> findByIdAndSubject(Long id, Subject subject);

    /**
     * Count the topics belonging to subjects owned by the given user.
     *
     * @param user the owning user
     * @return the number of topics the user owns
     */
    long countBySubject_User(User user);

    /**
     * Count the topics belonging to a specific subject.
     *
     * @param subject the subject
     * @return the number of topics in the subject
     */
    long countBySubject(Subject subject);

    /**
     * Topic counts per subject for a given user, scoped through the user's
     * subjects. Returns rows of [subject.id, subject.color, count].
     *
     * Used by the dashboard so topic counts come from one grouped query
     * instead of a per-subject N+1. Only subjects owned by the user are
     * considered.
     *
     * @param user the owning user
     * @return rows of [subjectId (Long), subjectColor (String), topicCount (Long)]
     */
    @Query("select t.subject.id, t.subject.color, count(t) " +
            "from Topic t where t.subject.user = :user group by t.subject.id, t.subject.color")
    List<Object[]> countTopicsPerSubjectForUser(User user);

    /**
     * Delete all topics belonging to a specific subject.
     * Used when deleting a subject to maintain referential integrity.
     *
     * @param subject the subject whose topics should be deleted
     */
    @Modifying
    @Query("DELETE FROM Topic t WHERE t.subject = :subject")
    void deleteBySubject(Subject subject);
}