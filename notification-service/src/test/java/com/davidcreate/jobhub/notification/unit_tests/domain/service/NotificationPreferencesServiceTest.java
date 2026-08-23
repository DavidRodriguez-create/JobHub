package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.service.NotificationPreferencesService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationPreferencesService Unit Tests")
class NotificationPreferencesServiceTest {

    @Mock NotificationPreferencesRepository repository;
    @InjectMocks NotificationPreferencesService service;

    private final UUID userId = UUID.randomUUID();

    // TC-01
    @Test
    @DisplayName("getPreferences returns contract defaults when no row exists, without upserting")
    void getPreferencesReturnsDefaultsWhenNoRow() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        NotificationPreferences result = service.getPreferences(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.isWeeklyDigestEmail()).isTrue();
        assertThat(result.isInAppNotificationsEnabled()).isFalse();
        assertThat(result.isInterviewReminders()).isTrue();
        assertThat(result.isGhostedAlert()).isTrue();
        verify(repository, never()).upsert(any());
    }

    // TC-02
    @Test
    @DisplayName("getPreferences returns the stored row exactly when one exists")
    void getPreferencesReturnsStoredRow() {
        NotificationPreferences stored = NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(false)
                .inAppNotificationsEnabled(true)
                .interviewReminders(false)
                .ghostedAlert(false)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(stored));

        NotificationPreferences result = service.getPreferences(userId);

        assertThat(result.isWeeklyDigestEmail()).isFalse();
        assertThat(result.isInAppNotificationsEnabled()).isTrue();
        assertThat(result.isInterviewReminders()).isFalse();
        assertThat(result.isGhostedAlert()).isFalse();
        verify(repository, never()).upsert(any());
    }

    // TC-03
    @Test
    @DisplayName("updatePreferences creates a row seeded with defaults plus the supplied field when none exists")
    void updatePreferencesCreatesRowWithDefaultsOnFirstUpdate() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, false);

        verify(repository).upsert(any());
        assertThat(result.isWeeklyDigestEmail()).isTrue();
        assertThat(result.isInAppNotificationsEnabled()).isFalse();
        assertThat(result.isInterviewReminders()).isTrue();
        assertThat(result.isGhostedAlert()).isFalse();
    }

    // TC-04
    @Test
    @DisplayName("updatePreferences merges a partial update into an existing row, preserving other fields")
    void updatePreferencesMergesPartialUpdate() {
        UUID existingId = UUID.randomUUID();
        NotificationPreferences existing = NotificationPreferences.builder()
                .id(existingId)
                .userId(userId)
                .weeklyDigestEmail(false)
                .inAppNotificationsEnabled(true)
                .interviewReminders(false)
                .ghostedAlert(true)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, false);

        assertThat(result.getId()).isEqualTo(existingId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.isWeeklyDigestEmail()).isFalse();
        assertThat(result.isInAppNotificationsEnabled()).isTrue();
        assertThat(result.isInterviewReminders()).isFalse();
        assertThat(result.isGhostedAlert()).isFalse();
    }

    // TC-05
    @Test
    @DisplayName("updatePreferences fully replaces an existing row when all four fields are supplied")
    void updatePreferencesFullUpdateReplacesAllFields() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(false)
                .interviewReminders(true)
                .ghostedAlert(true)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, false, true, false, false, false);

        assertThat(result.isWeeklyDigestEmail()).isFalse();
        assertThat(result.isInAppNotificationsEnabled()).isTrue();
        assertThat(result.isInterviewReminders()).isFalse();
        assertThat(result.isInterviewReminderEmail()).isFalse();
        assertThat(result.isGhostedAlert()).isFalse();
        verify(repository).upsert(any());
        verify(repository).findByUserId(userId);
    }

    // TC-05b — empty update on a user with no existing row still upserts defaults
    @Test
    @DisplayName("updatePreferences with an empty body creates a defaults row when none exists")
    void updatePreferencesEmptyBodyCreatesDefaultsRow() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, null);

        verify(repository).upsert(any());
        assertThat(result.isWeeklyDigestEmail()).isTrue();
        assertThat(result.isInAppNotificationsEnabled()).isFalse();
        assertThat(result.isInterviewReminders()).isTrue();
        assertThat(result.isGhostedAlert()).isTrue();
    }

    // TC-05b — empty update on a user with an existing row leaves all fields unchanged
    @Test
    @DisplayName("updatePreferences with an empty body leaves an existing row unchanged")
    void updatePreferencesEmptyBodyLeavesExistingRowUnchanged() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(false)
                .inAppNotificationsEnabled(true)
                .interviewReminders(false)
                .ghostedAlert(false)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, null);

        verify(repository).upsert(any());
        assertThat(result.isWeeklyDigestEmail()).isFalse();
        assertThat(result.isInAppNotificationsEnabled()).isTrue();
        assertThat(result.isInterviewReminders()).isFalse();
        assertThat(result.isGhostedAlert()).isFalse();
    }

    // TC-W2: getPreferences must not carry @Transactional (Story #136 perf fix);
    // updatePreferences must still carry it because it writes.
    @Test
    @DisplayName("TC-W2: getPreferences has no @Transactional; updatePreferences still does")
    void tcW2TransactionalAnnotationsAlignWithPerfFix() throws NoSuchMethodException {
        Method get = NotificationPreferencesService.class.getMethod("getPreferences", UUID.class);
        Method update = NotificationPreferencesService.class.getMethod("updatePreferences",
                UUID.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class, Boolean.class);

        assertThat(get.isAnnotationPresent(Transactional.class))
                .as("getPreferences must NOT be @Transactional (pure read; Story #136)")
                .isFalse();
        assertThat(update.isAnnotationPresent(Transactional.class))
                .as("updatePreferences must still be @Transactional (write path)")
                .isTrue();
    }

    // CR-153-U-001: interviewReminderEmail=true is persisted through updatePreferences
    @Test
    @DisplayName("CR-153-U-001: updatePreferences with interviewReminderEmail=true persists true")
    void cr153u001InterviewReminderEmailTruePersisted() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, true, null);

        assertThat(result.isInterviewReminderEmail()).isTrue();
    }

    // CR-153-U-002: interviewReminderEmail=false overwrites a stored true
    @Test
    @DisplayName("CR-153-U-002: updatePreferences with interviewReminderEmail=false persists false")
    void cr153u002InterviewReminderEmailFalsePersisted() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(false)
                .interviewReminders(true)
                .interviewReminderEmail(true)
                .ghostedAlert(true)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, false, null);

        assertThat(result.isInterviewReminderEmail()).isFalse();
    }

    // CR-153-U-003: omitting interviewReminderEmail (null) preserves the existing stored value
    @Test
    @DisplayName("CR-153-U-003: updatePreferences with interviewReminderEmail=null preserves existing value")
    void cr153u003InterviewReminderEmailNullPreservesExisting() {
        NotificationPreferences existing = NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(false)
                .interviewReminders(true)
                .interviewReminderEmail(true)
                .ghostedAlert(true)
                .build();
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, null);

        assertThat(result.isInterviewReminderEmail()).isTrue();
    }

    // CR-153-U-004: a brand-new user with all fields null defaults interviewReminderEmail to true
    @Test
    @DisplayName("CR-153-U-004: updatePreferences on new user with no fields defaults interviewReminderEmail to true")
    void cr153u004NewUserDefaultsInterviewReminderEmailTrue() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.upsert(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferences result = service.updatePreferences(userId, null, null, null, null, null);

        assertThat(result.isInterviewReminderEmail()).isTrue();
    }
}
