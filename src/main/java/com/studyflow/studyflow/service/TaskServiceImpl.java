package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.TaskDto;
import com.studyflow.studyflow.entity.RecurrenceType;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.Task;
import com.studyflow.studyflow.entity.TaskType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.TaskRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of TaskService.
 *
 * Contains the business logic for Task CRUD operations. Every operation
 * is scoped to the authenticated user resolved from the provided email:
 * tasks are created for subjects owned by that user and all reads/updates/deletes
 * are backed by user-scoped repository lookups, which prevents one user from
 * accessing another user's tasks (IDOR).
 */
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    /**
     * Constructor Injection.
     * Spring automatically injects the repositories.
     */
    public TaskServiceImpl(TaskRepository taskRepository,
                           SubjectRepository subjectRepository,
                           UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a new task and associate it with the subject (owned by the user).
     */
    @Override
    @Transactional
    public TaskDto saveTask(TaskDto taskDto, Long subjectId, String userEmail) {

        User user = requireUser(userEmail);
        Subject subject = requireOwnedSubject(subjectId, userEmail);

        // Convert DTO to Entity and assign the subject from the security context.
        Task task = new Task();
        task.setTitle(taskDto.title());
        task.setDetails(taskDto.details());
        task.setType(taskDto.type() != null ? taskDto.type() : TaskType.OTHER);
        task.setDueDate(taskDto.dueDate());
        task.setDueTime(taskDto.dueTime());
        task.setRecurrenceType(taskDto.recurrenceType() != null ? taskDto.recurrenceType() : RecurrenceType.ONCE);
        task.setProgress(taskDto.progress() != null ? taskDto.progress() : 0);
        task.setCompleted(taskDto.completed() != null ? taskDto.completed() : false);
        task.setSubject(subject);

        // Save Entity
        Task savedTask = taskRepository.save(task);

        // Convert Entity back to DTO
        return toDto(savedTask);
    }

    /**
     * Retrieve all tasks belonging to the authenticated user (via their subjects).
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getAllTasks(String userEmail) {

        User user = requireUser(userEmail);

        return taskRepository.findBySubject_UserOrderByDueDateAscIdAsc(user)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all tasks belonging to a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getTasksBySubject(Long subjectId, String userEmail) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);

        return taskRepository.findBySubjectOrderByDueDateAscIdAsc(subject)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve upcoming tasks (due date >= today) for the given user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getUpcomingTasks(String userEmail) {

        User user = requireUser(userEmail);
        LocalDate today = LocalDate.now();

        return taskRepository.findUpcomingByUser(user, today)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve past tasks (due date < today) for the given user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getPastTasks(String userEmail) {

        User user = requireUser(userEmail);
        LocalDate today = LocalDate.now();

        return taskRepository.findPastByUser(user, today)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve upcoming tasks for a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getUpcomingTasksBySubject(Long subjectId, String userEmail) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);
        LocalDate today = LocalDate.now();

        return taskRepository.findUpcomingBySubject(subject, today)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve past tasks for a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> getPastTasksBySubject(Long subjectId, String userEmail) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);
        LocalDate today = LocalDate.now();

        return taskRepository.findPastBySubject(subject, today)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Search tasks by title for the given user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> searchTasks(String userEmail, String searchTerm) {

        User user = requireUser(userEmail);

        return taskRepository.searchByUser(user, searchTerm)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Search tasks by title for a specific subject owned by the user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TaskDto> searchTasksBySubject(Long subjectId, String userEmail, String searchTerm) {

        Subject subject = requireOwnedSubject(subjectId, userEmail);

        return taskRepository.searchBySubject(subject, searchTerm)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a task by ID, only when it belongs to a subject owned by the authenticated user.
     */
    @Override
    @Transactional(readOnly = true)
    public TaskDto getTaskById(Long id, String userEmail) {

        Task task = requireOwnedTask(id, userEmail);

        return toDto(task);
    }

    /**
     * Update an existing task, after verifying ownership (via subject).
     */
    @Override
    @Transactional
    public TaskDto updateTask(Long id, TaskDto taskDto, String userEmail) {

        Task task = requireOwnedTask(id, userEmail);

        task.setTitle(taskDto.title());
        task.setDetails(taskDto.details());
        task.setType(taskDto.type() != null ? taskDto.type() : task.getType());
        task.setDueDate(taskDto.dueDate());
        task.setDueTime(taskDto.dueTime());
        task.setRecurrenceType(taskDto.recurrenceType() != null ? taskDto.recurrenceType() : task.getRecurrenceType());
        task.setProgress(taskDto.progress() != null ? taskDto.progress() : 0);
        task.setCompleted(taskDto.completed() != null ? taskDto.completed() : false);

        Task updatedTask = taskRepository.save(task);

        return toDto(updatedTask);
    }

    /**
     * Update task progress for a task owned by the given user.
     */
    @Override
    @Transactional
    public TaskDto updateTaskProgress(Long id, Integer progress, String userEmail) {

        Task task = requireOwnedTask(id, userEmail);

        int clampedProgress = Math.max(0, Math.min(100, progress));
        task.setProgress(clampedProgress);
        
        // Auto-complete if progress reaches 100%
        if (clampedProgress >= 100) {
            task.setCompleted(true);
        } else {
            task.setCompleted(false);
        }

        Task updatedTask = taskRepository.save(task);

        return toDto(updatedTask);
    }

    /**
     * Mark a task as complete for the given user.
     */
    @Override
    @Transactional
    public TaskDto completeTask(Long id, String userEmail) {

        Task task = requireOwnedTask(id, userEmail);

        task.setCompleted(true);
        task.setProgress(100);

        Task updatedTask = taskRepository.save(task);

        return toDto(updatedTask);
    }

    /**
     * Delete a task, only after verifying ownership (via subject).
     */
    @Override
    @Transactional
    public void deleteTask(Long id, String userEmail) {

        Task task = requireOwnedTask(id, userEmail);

        taskRepository.delete(task);
    }

    /**
     * Convert Task entity to TaskDto.
     */
    private TaskDto toDto(Task task) {
        return new TaskDto(
                task.getId(),
                task.getSubject().getId(),
                task.getSubject().getName(),
                task.getTitle(),
                task.getDetails(),
                task.getType(),
                task.getDueDate(),
                task.getDueTime(),
                task.getRecurrenceType(),
                task.getProgress(),
                task.getCompleted()
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
     * Ownership gate: the task must exist AND belong to a subject owned by the user.
     *
     * A single lookup avoids leaking whether the id exists at all.
     *
     * @throws AccessDeniedException when the task is not owned by the user
     */
    private Task requireOwnedTask(Long id, String userEmail) {
        User user = requireUser(userEmail);
        return taskRepository.findByIdAndSubject_User(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Task not found for this user"));
    }
}