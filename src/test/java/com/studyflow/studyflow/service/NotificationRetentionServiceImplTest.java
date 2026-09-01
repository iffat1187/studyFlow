package com.studyflow.studyflow.service;

import com.studyflow.studyflow.repository.NotificationRepository;
import com.studyflow.studyflow.service.impl.NotificationRetentionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationRetentionServiceImpl}.
 *
 * Confirms the conservative contract: only READ rows older than the window
 * are ever considered, nothing is deleted when there is nothing eligible,
 * and a non-positive window is a no-op.
 */
@ExtendWith(MockitoExtension.class)
class NotificationRetentionServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationRetentionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationRetentionServiceImpl(notificationRepository);
    }

    @Test
    void nothingEligible_removesNothing() {
        when(notificationRepository.countByReadTrueAndCreatedAtBefore(any())).thenReturn(0L);

        assertThat(service.cleanupReadOlderThan(90)).isZero();

        verify(notificationRepository, never()).deleteReadOlderThan(any());
    }

    @Test
    void eligible_removesAndReturnsCount() {
        when(notificationRepository.countByReadTrueAndCreatedAtBefore(any())).thenReturn(5L);
        when(notificationRepository.deleteReadOlderThan(any())).thenReturn(5);

        assertThat(service.cleanupReadOlderThan(90)).isEqualTo(5);
        verify(notificationRepository).deleteReadOlderThan(any());
    }

    @Test
    void nonPositiveWindow_isNoOp() {
        assertThat(service.cleanupReadOlderThan(0)).isZero();
        assertThat(service.cleanupReadOlderThan(-7)).isZero();

        verify(notificationRepository, never()).countByReadTrueAndCreatedAtBefore(any());
        verify(notificationRepository, never()).deleteReadOlderThan(any());
    }
}
