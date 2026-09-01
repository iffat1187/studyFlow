package com.studyflow.studyflow.service;

/**
 * Evaluates the study-relevant notification rules against the authenticated
 * user's real StudyFlow data and persists the notifications that are missing
 * (i.e. only the new ones), so repeated evaluation never produces duplicate
 * records.
 *
 * The rules are intentionally NOT placed in controllers, entities, templates
 * or JavaScript — they live here, server-side, operating on database data.
 *
 * Rules:
 *
 * 1. Task Due      - incomplete task due today
 * 2. Task Overdue  - incomplete task due before today
 * 3. Exam Upcoming - exam tomorrow
 * 4. Exam Today    - exam today
 * 5. Study Reminder- no valid study session recorded today
 * 6. Streak Reminder - active streak at risk because the user has not
 *                      studied today
 *
 * This sub-phase deliberately provides NO automatic scheduling: generation
 * is a manual, service-level operation that can be invoked and tested on
 * demand.
 */
public interface NotificationGenerationService {

    /**
     * Evaluate all notification rules for the authenticated user and persist
     * only the notifications that do not already exist for their event.
     *
     * The user is resolved from the authenticated email (never from the
     * browser) and every query is user-scoped, so rules only ever operate on
     * this user's tasks, exams, sessions and streak (IDOR prevention).
     *
     * @param userEmail the authenticated user's email
     * @return the number of newly created notifications (0 when nothing new)
     */
    int generateNotificationsForUser(String userEmail);
}
