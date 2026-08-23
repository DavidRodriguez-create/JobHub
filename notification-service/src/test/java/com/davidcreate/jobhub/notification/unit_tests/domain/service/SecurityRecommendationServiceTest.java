package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.TwoFactorStatusGateway;
import com.davidcreate.jobhub.notification.domain.service.NotificationCopyWriter;
import com.davidcreate.jobhub.notification.domain.service.SecurityRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityRecommendationService Unit Tests")
class SecurityRecommendationServiceTest {

    @Mock TwoFactorStatusGateway twoFactorStatusGateway;
    @Mock NotificationRepository notificationRepository;

    SecurityRecommendationService service;
    NotificationCopyWriter copyWriter;

    @BeforeEach
    void setUp() {
        copyWriter = new NotificationCopyWriter();
        service = new SecurityRecommendationService(twoFactorStatusGateway, notificationRepository, copyWriter);
    }

    // TC-NOTIF-U01: Scheduler creates SECURITY_RECOMMENDATION for user without 2FA
    @Test
    @DisplayName("TC-NOTIF-U01: creates SECURITY_RECOMMENDATION notification for user without 2FA")
    void createsSecurityRecommendationForUserWithoutTwoFactor() {
        UUID userId = UUID.randomUUID();

        when(twoFactorStatusGateway.fetchUsersWithoutTwoFactor()).thenReturn(List.of(userId));
        when(notificationRepository.existsByUserIdAndType(userId, NotificationType.SECURITY_RECOMMENDATION))
                .thenReturn(false);

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.SECURITY_RECOMMENDATION);
        assertThat(saved.getTitle()).isEqualTo("🛡️ Level up your account security!");
        assertThat(saved.getMessage()).isEqualTo(copyWriter.securityRecommendationMessage());
        assertThat(saved.getMessage()).contains("Two-factor authentication");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getApplicationId()).isNull();
    }

    // SR-U-10
    @Test
    @DisplayName("SR-U-10: persists the writer's exact title/message with applicationId null")
    void persistsExactTitleAndMessageWithApplicationIdNull() {
        UUID userId = UUID.randomUUID();

        when(twoFactorStatusGateway.fetchUsersWithoutTwoFactor()).thenReturn(List.of(userId));
        when(notificationRepository.existsByUserIdAndType(userId, NotificationType.SECURITY_RECOMMENDATION))
                .thenReturn(false);

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("🛡️ Level up your account security!");
        assertThat(saved.getMessage()).isEqualTo(
                "Two-factor authentication adds a second lock to your account, so a stolen password alone "
                        + "can't get anyone in. It takes about two minutes to set up in Settings, and "
                        + "future-you will thank present-you.");
        assertThat(saved.getApplicationId()).isNull();
    }

    // SR-U-11
    @Test
    @DisplayName("SR-U-11: applicationId stays null across multiple users with byte-identical copy")
    void applicationIdStaysNullAcrossMultipleUsersWithByteIdenticalCopy() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        when(twoFactorStatusGateway.fetchUsersWithoutTwoFactor()).thenReturn(List.of(userId1, userId2));
        when(notificationRepository.existsByUserIdAndType(userId1, NotificationType.SECURITY_RECOMMENDATION))
                .thenReturn(false);
        when(notificationRepository.existsByUserIdAndType(userId2, NotificationType.SECURITY_RECOMMENDATION))
                .thenReturn(false);

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getApplicationId()).isNull();
        assertThat(saved.get(1).getApplicationId()).isNull();
        assertThat(saved.get(0).getTitle()).isEqualTo(saved.get(1).getTitle());
        assertThat(saved.get(0).getMessage()).isEqualTo(saved.get(1).getMessage());
    }

    // TC-NOTIF-U02: Scheduler skips user who already received the notification
    @Test
    @DisplayName("TC-NOTIF-U02: skips user who already received the notification")
    void skipsUserWhoAlreadyReceivedNotification() {
        UUID userId = UUID.randomUUID();

        when(twoFactorStatusGateway.fetchUsersWithoutTwoFactor()).thenReturn(List.of(userId));
        when(notificationRepository.existsByUserIdAndType(userId, NotificationType.SECURITY_RECOMMENDATION))
                .thenReturn(true);

        service.run();

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // TC-NOTIF-U03: Scheduler skips user who enabled 2FA (not returned by auth internal endpoint)
    @Test
    @DisplayName("TC-NOTIF-U03: skips users not returned by auth internal endpoint")
    void skipsUsersNotReturnedByAuthInternalEndpoint() {
        when(twoFactorStatusGateway.fetchUsersWithoutTwoFactor()).thenReturn(List.of());

        service.run();

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(notificationRepository, never())
                .existsByUserIdAndType(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
