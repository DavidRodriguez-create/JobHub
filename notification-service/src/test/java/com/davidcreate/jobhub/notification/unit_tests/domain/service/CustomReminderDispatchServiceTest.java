package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderMailer;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import com.davidcreate.jobhub.notification.domain.service.CustomReminderDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomReminderDispatchService Unit Tests")
class CustomReminderDispatchServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC);

    @Mock CustomReminderRepository repository;
    @Mock NotificationPreferencesRepository preferencesRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserEmailGateway userEmailGateway;
    @Mock CustomReminderMailer mailer;

    private CustomReminderDispatchService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CustomReminderDispatchService(repository, preferencesRepository, notificationRepository,
                userEmailGateway, mailer, 200, FIXED_CLOCK);
    }

    private CustomReminder due(Set<CustomReminderChannel> channels, CustomReminderStatus status) {
        return due(channels, status, null);
    }

    private CustomReminder due(Set<CustomReminderChannel> channels, CustomReminderStatus status, String note) {
        return CustomReminder.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .applicationId(UUID.randomUUID())
                .title("Prep")
                .note(note)
                .triggerAtUtc(Instant.now(FIXED_CLOCK).minusSeconds(60))
                .channels(channels)
                .status(status)
                .build();
    }

    private NotificationPreferences prefs(boolean interviewReminderEmail) {
        return NotificationPreferences.builder()
                .userId(userId)
                .interviewReminderEmail(interviewReminderEmail)
                .build();
    }

    // CR-U-080
    @Test
    @DisplayName("CR-U-080: IN_APP channel always fired regardless of email prefs")
    void inAppAlwaysFired() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        verify(notificationRepository, times(1)).save(any());
        ArgumentCaptor<Set<CustomReminderChannel>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repository).markFired(eq(reminder.getId()), captor.capture(), any());
        assertThat(captor.getValue()).contains(CustomReminderChannel.IN_APP);
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    // CR-U-081
    @Test
    @DisplayName("CR-U-081: EMAIL channel dispatched when interviewReminderEmail=true")
    void emailDispatchedWhenEnabled() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(true)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(userId, "user@example.com"));

        service.run();

        verify(mailer, times(1)).send(eq("user@example.com"), eq(reminder));
        ArgumentCaptor<Set<CustomReminderChannel>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repository).markFired(eq(reminder.getId()), captor.capture(), any());
        assertThat(captor.getValue()).contains(CustomReminderChannel.EMAIL);
    }

    // CR-U-082
    @Test
    @DisplayName("CR-U-082: EMAIL channel dropped silently when interviewReminderEmail=false")
    void emailDroppedWhenDisabled() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(false)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(userId, "user@example.com"));

        assertDoesNotThrow(() -> service.run());

        verify(mailer, never()).send(any(), any());
        ArgumentCaptor<Set<CustomReminderChannel>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repository).markFired(eq(reminder.getId()), captor.capture(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    // CR-U-083
    @Test
    @DisplayName("CR-U-083: both channels requested, email gated off -- IN_APP fires, EMAIL dropped")
    void bothChannelsEmailGatedOff() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(false)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(userId, "user@example.com"));

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(mailer, never()).send(any(), any());
        ArgumentCaptor<Set<CustomReminderChannel>> captor = ArgumentCaptor.forClass(Set.class);
        verify(repository).markFired(eq(reminder.getId()), captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(CustomReminderChannel.IN_APP);
    }

    // CR-U-084
    @Test
    @DisplayName("CR-U-084: idempotent -- second dispatch on already-FIRED reminder is a no-op")
    void idempotentOnAlreadyFired() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP), CustomReminderStatus.FIRED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        assertDoesNotThrow(() -> service.run());

        verify(mailer, never()).send(any(), any());
        verify(notificationRepository, never()).save(any());
        verify(repository, never()).markFired(any(), any(), any());
    }

    // CR-U-085
    @Test
    @DisplayName("CR-U-085: email unresolvable -- EMAIL dropped, IN_APP still delivered")
    void emailUnresolvableDropsEmailOnly() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(true)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        assertDoesNotThrow(() -> service.run());

        verify(notificationRepository, times(1)).save(any());
        verify(mailer, never()).send(any(), any());
    }

    // CR-U-086
    @Test
    @DisplayName("CR-U-086: per-item failure isolation")
    void perItemFailureIsolation() {
        CustomReminder reminderA = due(Set.of(CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        CustomReminder reminderB = due(Set.of(CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminderA, reminderB));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs(true)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(userId, "user@example.com"));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp failure"))
                .when(mailer).send(anyString(), eq(reminderA));

        assertDoesNotThrow(() -> service.run());

        verify(mailer, times(1)).send(anyString(), eq(reminderB));
        verify(repository).markFired(eq(reminderB.getId()), any(), any());
    }

    // CR-U-087
    @Test
    @DisplayName("CR-U-087: no prefs row treated as defaults (email on, in-app on)")
    void noPrefsRowDefaultsToEmailOn() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL), CustomReminderStatus.SCHEDULED);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(userId, "user@example.com"));

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(mailer, times(1)).send(eq("user@example.com"), eq(reminder));
    }

    // TC-CR-U-40
    @Test
    @DisplayName("TC-CR-U-40: custom reminder notification carries applicationId regardless of message content (documented v1 naming gap)")
    void customReminderNotificationCarriesApplicationIdWithoutNote() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP), CustomReminderStatus.SCHEDULED, null);
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getApplicationId()).isEqualTo(reminder.getApplicationId());
    }

    // TC-CR-U-41
    @Test
    @DisplayName("TC-CR-U-41: custom reminder notification carries applicationId when the user-supplied note names the application")
    void customReminderNotificationCarriesApplicationIdWithNote() {
        CustomReminder reminder = due(Set.of(CustomReminderChannel.IN_APP), CustomReminderStatus.SCHEDULED, "Prep for Acme Corp interview");
        when(repository.findDue(any(), anyInt())).thenReturn(List.of(reminder));
        when(repository.markFired(any(), any(), any())).thenReturn(true);
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMessage()).contains("Prep for Acme Corp interview");
        assertThat(saved.getApplicationId()).isEqualTo(reminder.getApplicationId());
    }
}
