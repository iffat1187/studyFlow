package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.StudySessionDto;
import com.studyflow.studyflow.entity.StudySession;
import com.studyflow.studyflow.entity.StudySessionType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Topic;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.StudySessionRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TopicRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.StudySessionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of StudySessionService.
 *
 * Contains the business logic for Study Session CRUD operations. Every
 * operation is scoped to the authenticated user resolved from the provided
 * email: sessions are created for subjects owned by that user and all
 * reads/updates/deletes are backed by user-scoped repository lookups.
 *
 * Ownership is enforced through the subject chain:
 *
 *     Authenticated User → Subject → (optional Topic) → StudySession
 *
 * A submitted topicId is only ever resolved against the already-verified
 * subject (findByIdAndSubject), which rejects both another user's topics
 * and topics that belong to a different subject in a single query.
 */
@Service
public class StudySessionServiceImpl implements StudySessionService {

    private final StudySessionRepository studySessionRepository;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the repositories.
     */
    public StudySessionServiceImpl(StudySessionRepository studySessionRepository,
                                   SubjectRepository subjectRepository,
                                   TopicRepository topicRepository,
                                   UserRepository userRepository) {
        this.studySessionRepository = studySessionRepository;
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a new study session and associate it with the subject (owned by the user).
     */
    @Override
    @Transactional
    public StudySessionDto saveSession(StudySessionDto studySessionDto, String userEmail) {

        User user = requireUser(userEmail);
        Subject subject = requireOwnedSubject(studySessionDto.subjectId(), userEmail);
        Topic topic = resolveOptionalTopic(studySessionDto.topicId(), subject);

        // Convert DTO to Entity and assign the owner-verified subject/topic.
        StudySession session = new StudySession();
        applyFields(session, studySessionDto);
        session.setSubject(subject);
        session.setTopic(topic);

        // Save Entity
        StudySession savedSession = studySessionRepository.save(session);

        // Convert Entity back to DTO
        return toDto(savedSession);
    }

    /**
     * Retrieve all study sessions belonging to the authenticated user (via their subjects).
     */
    @Override
    @Transactional(readOnly = true)
    public List<StudySessionDto> getAllSessions(String userEmail) {

        User user = requireUser(userEmail);

        return studySessionRepository.findBySubject_UserOrderBySessionDateDescStartTimeDescIdDesc(user)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a study session by ID, only when it belongs to a subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public StudySessionDto getSessionById(Long id, String userEmail) {

        StudySession session = requireOwnedSession(id, userEmail);

        return toDto(session);
    }

    /**
     * Update an existing study session after verifying ownership.
     *
     * The session itself, the selected subject, and any selected topic are
     * all re-validated. The session may be moved to another subject owned by
     * the user, and the optional topic must belong to the newly selected
     * subject.
     */
    @Override
    @Transactional
    public StudySessionDto updateSession(Long id, StudySessionDto studySessionDto, String userEmail) {

        StudySession session = requireOwnedSession(id, userEmail);
        Subject subject = requireOwnedSubject(studySessionDto.subjectId(), userEmail);
        Topic topic = resolveOptionalTopic(studySessionDto.topicId(), subject);

        applyFields(session, studySessionDto);
        session.setSubject(subject);
        session.setTopic(topic);

        StudySession updatedSession = studySessionRepository.save(session);

        return toDto(updatedSession);
    }

    /**
     * Delete a study session, only after verifying ownership.
     */
    @Override
    @Transactional
    public void deleteSession(Long id, String userEmail) {

        StudySession session = requireOwnedSession(id, userEmail);

        studySessionRepository.delete(session);
    }

    /**
     * Copy the DTO's mutable session fields onto the entity.
     *
     * Duration is copied as the numeric minutes value; any "1h 30m" display
     * formatting belongs to the UI layer, never to the entity/database.
     */
    private void applyFields(StudySession session, StudySessionDto dto) {
        session.setSessionType(dto.sessionType() != null ? dto.sessionType() : StudySessionType.OTHER);
        session.setSessionDate(dto.sessionDate());
        session.setStartTime(dto.startTime());
        session.setEndTime(dto.endTime());
        session.setDurationMinutes(dto.durationMinutes());
        session.setNotes(dto.notes());
    }

    /**
     * Convert StudySession entity to StudySessionDto.
     *
     * subjectName/topicName are read-only display values; ownership is never
     * determined from them.
     */
    private StudySessionDto toDto(StudySession session) {
        Subject subject = session.getSubject();
        Topic topic = session.getTopic();
        return new StudySessionDto(
                session.getId(),
                subject != null ? subject.getId() : null,
                topic != null ? topic.getId() : null,
                subject != null ? subject.getName() : null,
                topic != null ? topic.getName() : null,
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getDurationMinutes(),
                session.getSessionType(),
                session.getNotes()
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
     * Ownership gate: the session must exist AND belong to a subject owned by the user.
     *
     * A single lookup avoids leaking whether the id exists at all.
     *
     * @throws AccessDeniedException when the session is not owned by the user
     */
    private StudySession requireOwnedSession(Long id, String userEmail) {
        User user = requireUser(userEmail);
        return studySessionRepository.findByIdAndSubject_User(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Study session not found for this user"));
    }

    /**
     * Resolve the optional topic for a session.
     *
     * The topic is looked up within the already-verified subject, which is
     * the single source of truth for both ownership and Topic↔Subject
     * consistency: a topic belonging to another user or to a different
     * subject is never returned here.
     *
     * @param topicId the submitted topic id (may be null)
     * @param subject the owner-verified subject
     * @return the matching topic, or null when no topic was submitted
     * @throws AccessDeniedException when the topic does not belong to the subject
     */
    private Topic resolveOptionalTopic(Long topicId, Subject subject) {
        if (topicId == null) {
            return null;
        }
        return topicRepository.findByIdAndSubject(topicId, subject)
                .orElseThrow(() -> new AccessDeniedException(
                        "Topic not found for this subject"));
    }
}