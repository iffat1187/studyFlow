package com.studyflow.studyflow.service;

/**
 * Conservative, automatic retention cleanup for the Notification table.
 *
 * Only {@code read = true} notification rows older than the configured
 * retention window are deleted. Unread notifications — however old — are
 * always preserved, and only the Notification table is ever touched (never
 * Users, Tasks, Exams, StudySessions, Subjects or Topics).
 */
public interface NotificationRetentionService {

    /**
     * Delete read notification rows older than the configured retention
     * window and return how many were removed.
     *
     * @param retentionDays notifications created more than this many days
     *                      ago (and already read) are removed; must be
     *                      greater than zero
     * @return the number of notification rows deleted
     */
    int cleanupReadOlderThan(int retentionDays);
}
