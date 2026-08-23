package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import com.davidcreate.jobhub.notification.domain.port.out.AlertMailer;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.StaleApplicationGateway;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import com.davidcreate.jobhub.notification.domain.service.GhostedAlertService;
import com.davidcreate.jobhub.notification.domain.service.NotificationCopyWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GhostedAlertService Unit Tests")
class GhostedAlertServiceTest {

    @Mock StaleApplicationGateway staleApplicationGateway;
    @Mock NotificationPreferencesRepository preferencesRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserEmailGateway userEmailGateway;
    @Mock AlertMailer alertMailer;

    GhostedAlertService service;
    NotificationCopyWriter copyWriter;

    private static final int STALE_DAYS = 14;

    @BeforeEach
    void setUp() {
        copyWriter = new NotificationCopyWriter();
        service = new GhostedAlertService(
                staleApplicationGateway,
                preferencesRepository,
                notificationRepository,
                userEmailGateway,
                alertMailer,
                copyWriter,
                STALE_DAYS);
    }

    private StaleApplication staleApp(UUID id, UUID userId, String company) {
        return staleApp(id, userId, company, "Backend Developer");
    }

    private StaleApplication staleApp(UUID id, UUID userId, String company, String jobTitle) {
        return StaleApplication.builder()
                .id(id)
                .userId(userId)
                .jobTitle(jobTitle)
                .company(company)
                .daysSinceLastActivity(STALE_DAYS)
                .build();
    }

    private NotificationPreferences prefs(UUID userId, boolean ghostedAlert) {
        return NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .ghostedAlert(ghostedAlert)
                .weeklyDigestEmail(false)
                .inAppNotificationsEnabled(true)
                .interviewReminders(false)
                .build();
    }

    // GA-NS-04: Happy path: status updated, GHOSTED_ALERT notification written with correct fields
    @Test
    @DisplayName("GA-NS-04: happy path: status updated, GHOSTED_ALERT notification written with correct fields")
    void happyPath_statusUpdatedAndNotificationWritten() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        verify(staleApplicationGateway, times(1)).updateApplicationStatusToGhosted(appId);
        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(com.davidcreate.jobhub.notification.domain.model.NotificationType.GHOSTED_ALERT);
        assertThat(saved.getTitle()).isEqualTo("👻 A wild ghost appeared!");
        assertThat(saved.getMessage()).isEqualTo(copyWriter.ghostedAlertMessage(app.getJobTitle()));
        assertThat(saved.getMessage()).contains("Backend Developer");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getApplicationId()).isEqualTo(appId);
    }

    // GA-NS-05: Email sent when preference on and user email verified
    @Test
    @DisplayName("GA-NS-05: email sent when preference on and user email verified")
    void emailSentWhenPreferenceOnAndUserVerified() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");
        String email = "user@example.com";

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of(userId, email));

        service.run();

        verify(alertMailer, times(1)).sendGhostedAlert(eq(email), eq(app));
    }

    // GA-NS-06: ghostedAlert=false -> no status update, no notification, no email
    @Test
    @DisplayName("GA-NS-06: ghostedAlert=false skips status update, notification, and email")
    void ghostedAlertFalseSkipsEverything() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, false)));

        service.run();

        verify(staleApplicationGateway, never()).updateApplicationStatusToGhosted(any());
        verify(notificationRepository, never()).save(any());
        verify(alertMailer, never()).sendGhostedAlert(anyString(), any());
    }

    // GA-NS-07: No preferences row defaults to ghostedAlert=true
    @Test
    @DisplayName("GA-NS-07: no preferences row defaults to ghostedAlert=true")
    void noPreferencesRowDefaultsToGhostedAlertTrue() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        verify(staleApplicationGateway, times(1)).updateApplicationStatusToGhosted(appId);
        verify(notificationRepository, times(1)).save(any());
    }

    // GA-NS-08: No stale apps -> zero side effects
    @Test
    @DisplayName("GA-NS-08: no stale apps causes zero side effects")
    void noStaleAppsZeroSideEffects() {
        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of());

        service.run();

        verify(preferencesRepository, never()).findByUserId(any());
        verify(notificationRepository, never()).save(any());
        verify(alertMailer, never()).sendGhostedAlert(anyString(), any());
    }

    // GA-NS-09: PUT failure for one app does not abort others (error isolation)
    @Test
    @DisplayName("GA-NS-09: PUT failure for one app does not abort processing others")
    void putFailureForOneAppDoesNotAbortOthers() {
        UUID app1Id = UUID.randomUUID();
        UUID app2Id = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        StaleApplication app1 = staleApp(app1Id, userId1, "Bad Corp");
        StaleApplication app2 = staleApp(app2Id, userId2, "Good Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app1, app2));
        when(preferencesRepository.findByUserId(userId1)).thenReturn(Optional.of(prefs(userId1, true)));
        when(preferencesRepository.findByUserId(userId2)).thenReturn(Optional.of(prefs(userId2, true)));
        doThrow(new RuntimeException("application-service 500")).when(staleApplicationGateway)
                .updateApplicationStatusToGhosted(app1Id);
        when(userEmailGateway.fetchEmails(Set.of(userId2))).thenReturn(Map.of());

        service.run();

        // app1 failed PUT: no notification for app1
        verify(notificationRepository, never()).save(
                argThat(n -> n.getApplicationId().equals(app1Id)));
        // app2 succeeded: notification written
        verify(staleApplicationGateway, times(1)).updateApplicationStatusToGhosted(app2Id);
        verify(notificationRepository, times(1)).save(
                argThat(n -> n.getApplicationId().equals(app2Id)));
    }

    // GA-NS-10: Notification write failure -> no email, no crash
    @Test
    @DisplayName("GA-NS-10: notification write failure skips email and does not crash")
    void notificationWriteFailureSkipsEmailAndNoCrash() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");
        String email = "user@example.com";

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        doThrow(new RuntimeException("DB write failure")).when(notificationRepository).save(any());
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of(userId, email));

        assertDoesNotThrow(() -> service.run());

        verify(alertMailer, never()).sendGhostedAlert(anyString(), any());
    }

    // GA-NS-11: company=null does not affect the message text (company is not interpolated
    // into the new copy at all, per PDA Section 1.1: only jobTitle is used)
    @Test
    @DisplayName("GA-NS-11: company=null does not change the playful message text")
    void companyNullDoesNotAffectMessage() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, null);

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        String message = captor.getValue().getMessage();
        assertThat(message).doesNotContain("[");
        assertThat(message).isEqualTo(copyWriter.ghostedAlertMessage(app.getJobTitle()));
        assertThat(message).contains("Backend Developer");
    }

    // GA-NS-12: Email unverified -> in-app written, email not sent
    @Test
    @DisplayName("GA-NS-12: email unverified causes in-app notification written but no email sent")
    void emailUnverifiedInAppWrittenEmailNotSent() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        // userId not in email map means unverified
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(alertMailer, never()).sendGhostedAlert(anyString(), any());
    }

    // GA-NS-13: PUT 409 is non-fatal, no notification written
    @Test
    @DisplayName("GA-NS-13: PUT 409 conflict is non-fatal and no notification written")
    void put409IsNonFatalNoNotificationWritten() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        doThrow(new com.davidcreate.jobhub.notification.domain.exception.ApplicationAlreadyGhostedException(appId))
                .when(staleApplicationGateway).updateApplicationStatusToGhosted(appId);

        assertDoesNotThrow(() -> service.run());

        verify(notificationRepository, never()).save(any());
    }

    // GA-NS-14: Stale query throws -> graceful failure, no crash
    @Test
    @DisplayName("GA-NS-14: stale query throwing causes graceful failure without crash")
    void staleQueryThrowsGracefulFailureNoCrash() {
        when(staleApplicationGateway.listStaleApplications(STALE_DAYS))
                .thenThrow(new RuntimeException("application-service unreachable"));

        assertDoesNotThrow(() -> service.run());

        verify(notificationRepository, never()).save(any());
        verify(alertMailer, never()).sendGhostedAlert(anyString(), any());
    }

    // Helper to use argThat with notification applicationId
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }

    // TC-GA-U-20
    @Test
    @DisplayName("TC-GA-U-20: ghosted notification message names the job title when known")
    void ghostedNotificationMessageNamesJobTitleWhenKnown() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp", "Senior Backend Engineer");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getMessage()).contains("Senior Backend Engineer");
        assertThat(saved.getApplicationId()).isEqualTo(app.getId());
    }

    // TC-GA-U-21
    @Test
    @DisplayName("TC-GA-U-21: ghosted notification with unknown job title still creates generic message and keeps applicationId set")
    void ghostedNotificationWithUnknownJobTitleKeepsApplicationId() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp", null);

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getMessage()).isEqualTo(copyWriter.ghostedAlertMessage(null));
        assertThat(saved.getMessage().toLowerCase()).doesNotContain("null").doesNotContain("undefined");
        assertThat(saved.getApplicationId()).isEqualTo(app.getId());
    }

    // GA-U-22
    @Test
    @DisplayName("GA-U-22: service persists the writer's exact title/message and keeps applicationId set")
    void servicePersistsWritersExactTitleMessageAndKeepsApplicationIdSet() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp", "Senior Backend Engineer");

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("👻 A wild ghost appeared!");
        assertThat(saved.getMessage()).isEqualTo(
                "Your application Senior Backend Engineer seems to have disappeared into the hiring void. "
                        + "If you're still interested in the position, a quick follow-up with the recruiter "
                        + "could bring it back to life. Don't give up! Your next opportunity might be just "
                        + "around the corner.");
        assertThat(saved.getApplicationId()).isEqualTo(appId);
        // AU-U-01 (maps to AC-AU-1): no code path in notification-service ever mints
        // APPLICATION_UPDATE; confirmed here on the GHOSTED_ALERT minting path's captured save.
        assertThat(saved.getType()).isNotEqualTo(NotificationType.APPLICATION_UPDATE);
    }

    // GA-U-23
    @Test
    @DisplayName("GA-U-23: fallback path preserves applicationId when jobTitle absent")
    void fallbackPathPreservesApplicationIdWhenJobTitleAbsent() {
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        StaleApplication app = staleApp(appId, userId, "Acme Corp", null);

        when(staleApplicationGateway.listStaleApplications(STALE_DAYS)).thenReturn(List.of(app));
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(userId, true)));
        when(userEmailGateway.fetchEmails(Set.of(userId))).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<com.davidcreate.jobhub.notification.domain.model.Notification> captor =
                ArgumentCaptor.forClass(com.davidcreate.jobhub.notification.domain.model.Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getMessage()).isEqualTo(
                "Your application seems to have disappeared into the hiring void. If you're still interested "
                        + "in the position, a quick follow-up with the recruiter could bring it back to life. "
                        + "Don't give up! Your next opportunity might be just around the corner.");
        assertThat(saved.getApplicationId()).isEqualTo(appId);
    }
}
