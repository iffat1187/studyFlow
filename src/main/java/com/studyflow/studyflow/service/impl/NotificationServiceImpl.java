package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.dto.NotificationDto;
import com.studyflow.studyflow.entity.Notification;
import com.studyflow.studyflow.entity.NotificationType;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link NotificationService}.
 *
 * Every operation is scoped to the authenticated user, resolved from the
 * email taken from the security context (never from the browser). An unknown
 * account (or one with no matching records) yields an empty list or a null
 * result rather than fabricated data, so user isolation holds even under
 * manipulated identity input.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Constructor injection of the required repositories.
     */
    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public NotificationDto createNotification(String userEmail, String title, String message,
                                              NotificationType type) {
        return createNotification(userEmail, title, message, type, null);
    }

    @Override
    @Transactional
    public NotificationDto createNotification(String userEmail, String title, String message,
                                              NotificationType type, String sourceKey) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return null;
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setSourceKey(sourceKey);
        // read defaults to false; createdAt is set by @PrePersist server-side.
        notification.setRead(false);

        return NotificationDto.from(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return List.of();
        }
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return List.of();
        }
        return notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return 0;
        }
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(String userEmail, int limit) {
        User user = resolveUser(userEmail);
        if (user == null || limit <= 0) {
            return List.of();
        }
        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, limit)).stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Override
    @Transactional
    public boolean markAsRead(String userEmail, Long id) {
        User user = resolveUser(userEmail);
        if (user == null || id == null) {
            return false;
        }
        return notificationRepository.findByIdAndUser(id, user)
                .map(notification -> {
                    if (Boolean.TRUE.equals(notification.getRead())) {
                        return true; // already read; treated as success
                    }
                    notification.setRead(true);
                    notificationRepository.save(notification);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public int markAllAsRead(String userEmail) {
        User user = resolveUser(userEmail);
        if (user == null) {
            return 0;
        }
        return notificationRepository.markAllReadByUser(user);
    }

    /**
     * Resolve the authenticated user record, or null when it cannot be found.
     */
    private User resolveUser(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(userEmail).orElse(null);
    }
}
