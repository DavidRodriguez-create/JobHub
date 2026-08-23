package com.davidcreate.jobhub.notification.unit_tests.domain.model;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidChannelsException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidTitleException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderTriggerInPastException;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("CustomReminder domain invariants")
class CustomReminderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC);

    // CR-U-001
    @Test
    @DisplayName("CR-U-001: triggerAtUtc strictly in future is accepted")
    void triggerStrictlyInFutureAccepted() {
        Instant future = Instant.now(FIXED_CLOCK).plusSeconds(60);
        assertDoesNotThrow(() -> CustomReminder.validateTrigger(future, FIXED_CLOCK));
    }

    // CR-U-002
    @Test
    @DisplayName("CR-U-002: triggerAtUtc equal to now is rejected")
    void triggerEqualToNowRejected() {
        Instant now = Instant.now(FIXED_CLOCK);
        assertThatThrownBy(() -> CustomReminder.validateTrigger(now, FIXED_CLOCK))
                .isInstanceOf(CustomReminderTriggerInPastException.class);
    }

    // CR-U-003
    @Test
    @DisplayName("CR-U-003: triggerAtUtc one second in the past is rejected")
    void triggerOneSecondInPastRejected() {
        Instant past = Instant.now(FIXED_CLOCK).minusSeconds(1);
        assertThatThrownBy(() -> CustomReminder.validateTrigger(past, FIXED_CLOCK))
                .isInstanceOf(CustomReminderTriggerInPastException.class);
    }

    // CR-U-004
    @Test
    @DisplayName("CR-U-004: empty channels list is rejected")
    void emptyChannelsRejected() {
        assertThatThrownBy(() -> CustomReminder.normaliseChannels(List.of()))
                .isInstanceOf(CustomReminderInvalidChannelsException.class);
    }

    // CR-U-005
    @Test
    @DisplayName("CR-U-005: duplicate channels normalised to one entry, not rejected")
    void duplicateChannelsNormalised() {
        Set<CustomReminderChannel> result = CustomReminder.normaliseChannels(
                List.of(CustomReminderChannel.IN_APP, CustomReminderChannel.IN_APP));

        assertThat(result).containsExactly(CustomReminderChannel.IN_APP);
    }

    // CR-U-006
    @Test
    @DisplayName("CR-U-006: both channels [IN_APP, EMAIL] is accepted")
    void bothChannelsAccepted() {
        Set<CustomReminderChannel> result = CustomReminder.normaliseChannels(
                List.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL));

        assertThat(result).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL);
    }

    // CR-U-007
    @Test
    @DisplayName("CR-U-007: title blank is rejected")
    void titleBlankRejected() {
        assertThatThrownBy(() -> CustomReminder.validateTitle("", 200))
                .isInstanceOf(CustomReminderInvalidTitleException.class);
    }

    // CR-U-008
    @Test
    @DisplayName("CR-U-008: title at max length 200 chars is accepted")
    void titleAtMaxLengthAccepted() {
        String title = "a".repeat(200);
        assertDoesNotThrow(() -> CustomReminder.validateTitle(title, 200));
    }

    // CR-U-009
    @Test
    @DisplayName("CR-U-009: title at 201 chars is rejected")
    void titleTooLongRejected() {
        String title = "a".repeat(201);
        assertThatThrownBy(() -> CustomReminder.validateTitle(title, 200))
                .isInstanceOf(CustomReminderInvalidTitleException.class);
    }

    // CR-U-010
    @Test
    @DisplayName("CR-U-010: note at max length 2000 chars is accepted")
    void noteAtMaxLengthAccepted() {
        String note = "a".repeat(2000);
        assertDoesNotThrow(() -> CustomReminder.validateNote(note, 2000));
    }

    // CR-U-011
    @Test
    @DisplayName("CR-U-011: note at 2001 chars is rejected")
    void noteTooLongRejected() {
        String note = "a".repeat(2001);
        assertThatThrownBy(() -> CustomReminder.validateNote(note, 2000))
                .isInstanceOf(CustomReminderInvalidTitleException.class);
    }

    // CR-U-012
    @Test
    @DisplayName("CR-U-012: stage is optional; null stage is accepted")
    void nullStageAccepted() {
        CustomReminder reminder = CustomReminder.builder()
                .stage(null)
                .build();

        assertThat(reminder.getStage()).isNull();
    }

    // CR-U-013
    @Test
    @DisplayName("CR-U-013: [EMAIL] is force-normalised to [IN_APP, EMAIL]")
    void emailOnlyForceAddsInApp() {
        Set<CustomReminderChannel> result = CustomReminder.normaliseChannels(
                List.of(CustomReminderChannel.EMAIL));

        assertThat(result).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL);
    }

    // CR-U-014
    @Test
    @DisplayName("CR-U-014: [IN_APP] is unchanged")
    void inAppOnlyUnchanged() {
        Set<CustomReminderChannel> result = CustomReminder.normaliseChannels(
                List.of(CustomReminderChannel.IN_APP));

        assertThat(result).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP);
    }

    // CR-U-015
    @Test
    @DisplayName("CR-U-015: [IN_APP, EMAIL] is unchanged")
    void inAppAndEmailUnchanged() {
        Set<CustomReminderChannel> result = CustomReminder.normaliseChannels(
                List.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL));

        assertThat(result).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL);
    }
}
