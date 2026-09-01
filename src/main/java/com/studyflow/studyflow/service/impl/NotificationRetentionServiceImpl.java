package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.service.NotificationRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of {@link NotificationRetentionService}.
 *
 * The retention window is computed with {@link LocalDateTime#now()} in the
 * application timezone (see {@code TimeZoneConfig}), consistent with the
 * timestamps the generation service writes. Cleanup is idempotent and
 * conservative: it only ever removes read rows older than the window.
 */
@Service
public class NotificationRetentionServiceImpl implements NotificationRetentionService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionServiceImpl.class);

    private final NotificationRepository notificationRepository;

    /**
     * Constructor injection.
     */
    public NotificationRetentionServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public int cleanupReadOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            log.warn("Notification retention skipped: number of days must be positive (got {}).",
                    retentionDays);
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long eligible = notificationRepository.countByReadTrueAndCreatedAtBefore(cutoff);
        int removed = 0;
        if (eligible > 0) {
            removed = notificationRepository.deleteReadOlderThan(cutoff);
            log.info("Notification retention: removed {} read notification(s) older than {} day(s).",
                    removed, retentionDays);
        } else {
            log.info("Notification retention: no read notifications older than {} day(s) to remove.",
                    retentionDays);
        }
        return removed;
    }
}
