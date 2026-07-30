package com.studyflow.studyflow.repository;

import com.studyflow.studyflow.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Subject entity.
 *
 * JpaRepository provides built-in CRUD operations
 * such as save(), findAll(), findById(), deleteById(), etc.
 */
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

}
