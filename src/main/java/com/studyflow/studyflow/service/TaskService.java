package com.studyflow.studyflow.service;

import com.studyflow.studyflow.dto.TaskDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for Task operations.
 *
 * Every operation is ownership-aware: it takes the authenticated user's
 * email and the implementation resolves that user, then queries only the
 * tasks the user owns (via their subjects). The owner is derived from the
 * authentication context, never from the request payload.
 */
public interface TaskService {

    /**
     * Create a new task and assign it to the given subject (owned by the user).
     *
     * @param taskDto   task data received from the form
     * @param subjectId the subject this task belongs to
     * @param userEmail the authenticated user's email
     * @return saved task information
     */
    TaskDto saveTask(TaskDto taskDto, Long subjectId, String userEmail);


    /**
     * Retrieve all tasks owned by the given user (via their subjects).
     *
     * @param userEmail the authenticated user's email
     * @return the user's tasks only
     */
    List<TaskDto> getAllTasks(String userEmail);


    /**
     * Retrieve all tasks belonging to a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @return the subject's tasks
     */
    List<TaskDto> getTasksBySubject(Long subjectId, String userEmail);


    /**
     * Retrieve upcoming tasks (due date >= today) for the given user.
     *
     * @param userEmail the authenticated user's email
     * @return the user's upcoming tasks
     */
    List<TaskDto> getUpcomingTasks(String userEmail);


    /**
     * Retrieve past tasks (due date < today) for the given user.
     *
     * @param userEmail the authenticated user's email
     * @return the user's past tasks
     */
    List<TaskDto> getPastTasks(String userEmail);


    /**
     * Retrieve upcoming tasks for a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @return the subject's upcoming tasks
     */
    List<TaskDto> getUpcomingTasksBySubject(Long subjectId, String userEmail);


    /**
     * Retrieve past tasks for a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @return the subject's past tasks
     */
    List<TaskDto> getPastTasksBySubject(Long subjectId, String userEmail);


    /**
     * Search tasks by title for the given user.
     *
     * @param userEmail the authenticated user's email
     * @param searchTerm the search term
     * @return matching tasks
     */
    List<TaskDto> searchTasks(String userEmail, String searchTerm);


    /**
     * Search tasks by title for a specific subject owned by the user.
     *
     * @param subjectId the subject primary key
     * @param userEmail the authenticated user's email
     * @param searchTerm the search term
     * @return matching tasks
     */
    List<TaskDto> searchTasksBySubject(Long subjectId, String userEmail, String searchTerm);


    /**
     * Find a task belonging to the given user (via subject ownership).
     *
     * @param id        task primary key
     * @param userEmail the authenticated user's email
     * @return task information if it belongs to the user
     * @throws org.springframework.security.access.AccessDeniedException
     *         when the task does not belong to the user (or does not exist)
     */
    TaskDto getTaskById(Long id, String userEmail);


    /**
     * Update a task belonging to the given user.
     *
     * Ownership is verified before any change is applied.
     *
     * @param id         existing task id
     * @param taskDto    updated task information
     * @param userEmail  the authenticated user's email
     * @return updated task information
     */
    TaskDto updateTask(Long id, TaskDto taskDto, String userEmail);


    /**
     * Update task progress (0-100) for a task owned by the given user.
     *
     * @param id        task primary key
     * @param progress  progress percentage (0-100)
     * @param userEmail the authenticated user's email
     * @return updated task information
     */
    TaskDto updateTaskProgress(Long id, Integer progress, String userEmail);


    /**
     * Mark a task as complete for the given user.
     *
     * Sets completed=true and progress=100.
     *
     * @param id        task primary key
     * @param userEmail the authenticated user's email
     * @return updated task information
     */
    TaskDto completeTask(Long id, String userEmail);


    /**
     * Delete a task belonging to the given user.
     *
     * Ownership is verified before the destructive operation.
     *
     * @param id        task primary key
     * @param userEmail the authenticated user's email
     */
    void deleteTask(Long id, String userEmail);

}