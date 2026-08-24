package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Subject entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 *
 * Every query used by user-facing pages is scoped to the owning
 * {@link User} so that one user can never observe another user's subjects.
 */
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    /**
     * All subjects owned by the given user, ordered by id for stable output.
     *
     * @param user the owning user
     * @return the user's subjects
     */
    List<Subject> findAllByUserOrderByIdAsc(User user);

    /**
     * Find a subject by id only when it belongs to the given user.
     *
     * @param id   subject primary key
     * @param user the owning user
     * @return the subject when owned by the user, empty otherwise
     */
    Optional<Subject> findByIdAndUser(Long id, User user);

    /**
     * Count the subjects owned by the given user.
     *
     * @param user the owning user
     * @return the number of subjects the user owns
     */
    long countByUser(User user);

}
