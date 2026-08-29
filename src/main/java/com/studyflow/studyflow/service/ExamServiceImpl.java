package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ExamService.
 *
 * Contains the business logic for Exam CRUD operations. Every operation
 * is scoped to the authenticated user resolved from the provided email:
 * exams are created for subjects owned by that user and all reads/updates/deletes
 * are backed by user-scoped repository lookups, which prevents one user from
 * accessing another user's exams (IDOR).
 */
@Service
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the repositories.
     */
    public ExamServiceImpl(ExamRepository examRepository,
                           SubjectRepository subjectRepository,
                           UserRepository userRepository) {
        this.examRepository = examRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a new exam and associate it with the subject (owned by the user).
     */
    @Override
    @Transactional
    public ExamDto saveExam(ExamDto examDto, Long subjectId, String userEmail) {

        User user = requireUser(userEmail);
        Subject subject = requireOwnedSubject(subjectId, userEmail);

        // Convert DTO to Entity and assign the subject from the security context.
        Exam exam = new Exam();
        exam.setName(examDto.name());
        exam.setExamType(examDto.examType() != null ? examDto.examType() : ExamType.OTHER);
        exam.setExamDate(examDto.examDate());
        exam.setExamTime(examDto.examTime());
        exam.setDurationMinutes(examDto.durationMinutes());
        exam.setDetails(examDto.details());
        exam.setSubject(subject);

        // Save Entity
        Exam savedExam = examRepository.save(exam);

        // Convert Entity back to DTO
        return toDto(savedExam);
    }

    /**
     * Retrieve all exams belonging to the authenticated user (via their subjects).
     */
    @Override
    @Transactional(readOnly = true)
    public List<ExamDto> getAllExams(String userEmail) {

        User user = requireUser(userEmail);

        return examRepository.findBySubject_UserOrderByExamDateAscExamTimeAscIdAsc(user)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve the user's exams (upcoming or past) with optional filters.
     *
     * Filtering is delegated to user-scoped repository queries so no other
     * user's exams can leak in, and blank search terms are normalized to null.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ExamDto> getExams(String userEmail, String search, Long subjectId,
                                  ExamType examType, boolean upcoming) {

        User user = requireUser(userEmail);

        String normalizedSearch = (search == null || search.trim().isEmpty())
                ? null
                : search.trim();

        List<Exam> exams = upcoming
                ? examRepository.findUpcoming(user, LocalDate.now(), normalizedSearch, subjectId, examType)
                : examRepository.findPast(user, LocalDate.now(), normalizedSearch, subjectId, examType);

        return exams.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all exams belonging to a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ExamDto> getExamsBySubject(Long subjectId, String userEmail) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);

        return examRepository.findBySubjectOrderByExamDateAscExamTimeAscIdAsc(subject)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve an exam by ID, only when it belongs to a subject owned by the authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public ExamDto getExamById(Long id, String userEmail) {

        Exam exam = requireOwnedExam(id, userEmail);

        return toDto(exam);
    }

    /**
     * Update an existing exam, after verifying ownership (via subject).
     *
     * The exam may be moved to a different subject: when it is, the target
     * subject comes from {@code examDto.subjectId()} and is itself verified
     * to belong to the user before the reassignment is applied.
     */
    @Override
    @Transactional
    public ExamDto updateExam(Long id, ExamDto examDto, String userEmail) {

        Exam exam = requireOwnedExam(id, userEmail);

        // Optional subject reassignment: the target subject must be owned by the user.
        Long targetSubjectId = examDto.subjectId();
        if (targetSubjectId != null
                && (exam.getSubject() == null || !targetSubjectId.equals(exam.getSubject().getId()))) {
            Subject targetSubject = requireOwnedSubject(targetSubjectId, userEmail);
            exam.setSubject(targetSubject);
        }

        exam.setName(examDto.name());
        exam.setExamType(examDto.examType() != null ? examDto.examType() : exam.getExamType());
        exam.setExamDate(examDto.examDate() != null ? examDto.examDate() : exam.getExamDate());
        exam.setExamTime(examDto.examTime());
        exam.setDurationMinutes(examDto.durationMinutes() != null ? examDto.durationMinutes() : exam.getDurationMinutes());
        exam.setDetails(examDto.details());

        Exam updatedExam = examRepository.save(exam);

        return toDto(updatedExam);
    }

    /**
     * Delete an exam, only after verifying ownership (via subject).
     */
    @Override
    @Transactional
    public void deleteExam(Long id, String userEmail) {

        Exam exam = requireOwnedExam(id, userEmail);

        examRepository.delete(exam);
    }

    /**
     * True when the user owns at least one exam (database-side existence check).
     */
    @Override
    @Transactional(readOnly = true)
    public boolean userHasAnyExam(String userEmail) {
        User user = requireUser(userEmail);
        return examRepository.existsBySubject_User(user);
    }

    /**
     * Convert Exam entity to ExamDto.
     */
    private ExamDto toDto(Exam exam) {
        return new ExamDto(
                exam.getId(),
                exam.getSubject() != null ? exam.getSubject().getId() : null,
                exam.getSubject() != null ? exam.getSubject().getName() : null,
                exam.getName(),
                exam.getExamType(),
                exam.getExamDate(),
                exam.getExamTime(),
                exam.getDurationMinutes(),
                exam.getDetails()
        );
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
     * @throws AccessDeniedException when the subject is not owned by the user
     */
    private Subject requireOwnedSubject(Long subjectId, String userEmail) {
        User user = requireUser(userEmail);
        return subjectRepository.findByIdAndUser(subjectId, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Subject not found for this user"));
    }

    /**
     * Ownership gate: the exam must exist AND belong to a subject owned by the user.
     *
     * A single lookup avoids leaking whether the id exists at all.
     *
     * @throws AccessDeniedException when the exam is not owned by the user
     */
    private Exam requireOwnedExam(Long id, String userEmail) {
        User user = requireUser(userEmail);
        return examRepository.findByIdAndSubject_User(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Exam not found for this user"));
    }
}