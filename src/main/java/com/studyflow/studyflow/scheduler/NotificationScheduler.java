package com.studyflow.studyflow.scheduler;

import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationGenerationService;
import com.studyflow.studyflow.service.NotificationRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the notification generation rules automatically for every active
 * user.
 *
 * Scheduling is the only responsibility of this component. It deliberately
 * contains NO notification business rules — it simply fans the configured
 * daily run out to {@link NotificationGenerationService}, which owns the
 * Sub-Phase B rules, idempotency and per-user transaction scoping.
 *
 * Architecture:
 *
 * <pre>
 * NotificationScheduler
 *         |  (per enabled user, own transaction)
 *         v
 * NotificationGenerationService
 *         |
 * NotificationService
 *         |
 * NotificationRepository
 * </pre>
 *
 * Multi-user + failure isolation: each user's generation runs inside its own
 * {@code @Transactional} call on the generation service, so a transient
 * problem with one user's data is caught and logged here without stopping
 * the other users. A single user's failure never aborts the whole batch.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationGenerationService generationService;
    private final UserRepository userRepository;
    private final NotificationRetentionService retentionService;
    private final int retentionDays;

    /**
     * Constructor injection.
     *
     * @param generationService generates notifications per active user
     * @param userRepository     resolves the set of active users
     * @param retentionService   conservative read-notification cleanup
     * @param retentionDays      retention window in days (configurable)
     */
    public NotificationScheduler(NotificationGenerationService generationService,
                                 UserRepository userRepository,
                                 NotificationRetentionService retentionService,
                                 @Value("${notification.retention.days:90}") int retentionDays) {
        this.generationService = generationService;
        this.userRepository = userRepository;
        this.retentionService = retentionService;
        this.retentionDays = retentionDays;
    }

    /**
     * Daily notification generation for all enabled users.
     *
     * The cron expression and timezone are configured via
     * {@code notification.scheduler.cron} and {@code studyflow.timezone}; the
     * same timezone also drives the JVM default (see {@code TimeZoneConfig}),
     * so the trigger time and the generation service's "today" always agree.
     *
     * The generation rules are idempotent (keyed by deterministic
     * {@code sourceKey}), so restarting the application, running the job more
     * than once, or re-triggering it never creates duplicate notification
     * records.
     */
    @Scheduled(cron = "${notification.scheduler.cron}", zone = "${studyflow.timezone}")
    public void generateDailyNotifications() {
        log.info("Notification generation started.");

        int totalCreated = 0;
        int failures = 0;

        for (User user : userRepository.findByEnabledTrue()) {
            String email = user.getEmail();
            try {
                int created = generationService.generateNotificationsForUser(email);
                totalCreated += created;
                log.info("Generated {} notification(s) for user {}.", created, email);
            } catch (RuntimeException ex) {
                failures++;
                log.error("Failed to generate notifications for user {}.", email, ex);
            }
        }

        if (failures > 0) {
            log.warn("Notification generation completed with {} failure(s). Total generated {}.", failures, totalCreated);
        } else {
            log.info("Notification generation completed. Generated {} notification(s) total.", totalCreated);
        }
    }

    /**
     * Low-frequency, conservative retention maintenance.
     *
     * Runs on a separate weekly schedule ({@code notification.retention.cron})
     * and delegates to {@link NotificationRetentionService}, which only ever
     * removes READ notification rows older than {@code notification.retention.days}.
     * Unread notifications and every other table (Users, Tasks, Exams,
     * StudySessions, Subjects, Topics) are never touched.
     */
    @Scheduled(cron = "${notification.retention.cron}")
    public void cleanupExpiredNotifications() {
        log.info("Notification retention maintenance started.");
        retentionService.cleanupReadOlderThan(retentionDays);
        log.info("Notification retention maintenance completed.");
    }
}
