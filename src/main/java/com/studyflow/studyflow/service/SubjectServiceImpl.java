package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.SubjectDto;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SubjectService.
 *
 * Contains the business logic for Subject CRUD operations. Every operation
 * is scoped to the authenticated user resolved from the provided email:
 * subjects are created for that user and all reads/updates/deletes are
 * backed by user-scoped repository lookups, which prevents one user from
 * accessing another user's subjects (IDOR).
 */
@Service
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final TaskRepository taskRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the repositories.
     */
    public SubjectServiceImpl(SubjectRepository subjectRepository,
                              TopicRepository topicRepository,
                              TaskRepository taskRepository,
                              ExamRepository examRepository,
                              UserRepository userRepository) {
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.taskRepository = taskRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a new subject and associate it with the authenticated user.
     */
    @Override
    @Transactional
    public SubjectDto saveSubject(SubjectDto subjectDto, String userEmail) {

        User user = requireUser(userEmail);

        // Convert DTO to Entity and assign the owner from the security context.
        Subject subject = new Subject();
        subject.setName(subjectDto.name());
        subject.setDescription(subjectDto.description());
        subject.setColor(subjectDto.color());
        subject.setUser(user);

        // Save Entity
        Subject savedSubject = subjectRepository.save(subject);

        // Convert Entity back to DTO (new subject has 0 topics)
        return new SubjectDto(
                savedSubject.getId(),
                savedSubject.getName(),
                savedSubject.getDescription(),
                savedSubject.getColor(),
                0L
        );
    }

    /**
     * Retrieve all subjects belonging to the authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SubjectDto> getAllSubjects(String userEmail) {

        User user = requireUser(userEmail);

        return subjectRepository.findAllByUserOrderByIdAsc(user)
                .stream()
                .map(subject -> new SubjectDto(
                        subject.getId(),
                        subject.getName(),
                        subject.getDescription(),
                        subject.getColor(),
                        topicRepository.countBySubject(subject)
                ))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a subject by ID, only when it belongs to the authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public SubjectDto getSubjectById(Long id, String userEmail) {

        Subject subject = requireOwnedSubject(id, userEmail);

        return new SubjectDto(
                subject.getId(),
                subject.getName(),
                subject.getDescription(),
                subject.getColor(),
                topicRepository.countBySubject(subject)
        );
    }

    /**
     * Update an existing subject, after verifying ownership.
     */
    @Override
    @Transactional
    public SubjectDto updateSubject(Long id, SubjectDto subjectDto, String userEmail) {

        Subject subject = requireOwnedSubject(id, userEmail);

        subject.setName(subjectDto.name());
        subject.setDescription(subjectDto.description());
        subject.setColor(subjectDto.color());

        Subject updatedSubject = subjectRepository.save(subject);

        return new SubjectDto(
                updatedSubject.getId(),
                updatedSubject.getName(),
                updatedSubject.getDescription(),
                updatedSubject.getColor(),
                topicRepository.countBySubject(updatedSubject)
        );
    }

    /**
     * Delete a subject, only after verifying ownership.
     * If the subject has topics or tasks, they are deleted first to maintain referential integrity.
     */
    @Override
    @Transactional
    public void deleteSubject(Long id, String userEmail) {

        Subject subject = requireOwnedSubject(id, userEmail);

        // Delete associated tasks first to avoid foreign key constraint violations
        taskRepository.deleteBySubject(subject);

        // Delete associated exams first to avoid foreign key constraint violations
        examRepository.deleteBySubject(subject);

        // Delete associated topics first to avoid foreign key constraint violations
        topicRepository.deleteBySubject(subject);

        subjectRepository.delete(subject);
    }

    /**
     * Resolve the authenticated user from the security context email.
     */
    private User requireUser(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found for the authenticated email"));
    }

    /**
     * Ownership gate: the subject must exist AND belong to the user.
     *
     * A single lookup avoids leaking whether the id exists at all.
     *
     * @throws AccessDeniedException when the subject is not owned by the user
     */
    private Subject requireOwnedSubject(Long id, String userEmail) {
        User user = requireUser(userEmail);
        return subjectRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Subject not found for this user"));
    }
}