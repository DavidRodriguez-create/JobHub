package com.davidcreate.jobhub.notification.domain.model;

import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidChannelsException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderInvalidTitleException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderTriggerInPastException;
import lombok.Builder;
import lombok.Getter;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class CustomReminder {

    public static final int TITLE_MAX_LENGTH_DEFAULT = 200;
    public static final int NOTE_MAX_LENGTH_DEFAULT = 2000;

    private final UUID id;
    private final UUID userId;
    private final UUID applicationId;
    private final String title;
    private final String note;
    private final Instant triggerAtUtc;
    private final Set<CustomReminderChannel> channels;
    private final CustomReminderStage stage;
    private final CustomReminderStatus status;
    private final Set<CustomReminderChannel> channelsFired;
    private final Instant firedAtUtc;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static void validateTrigger(Instant triggerAtUtc, Clock clock) {
        if (triggerAtUtc == null || !triggerAtUtc.isAfter(Instant.now(clock))) {
            throw new CustomReminderTriggerInPastException();
        }
    }

    public static Set<CustomReminderChannel> normaliseChannels(Iterable<CustomReminderChannel> rawChannels) {
        Set<CustomReminderChannel> normalised = EnumSet.noneOf(CustomReminderChannel.class);
        if (rawChannels != null) {
            for (CustomReminderChannel channel : rawChannels) {
                if (channel != null) {
                    normalised.add(channel);
                }
            }
        }
        if (normalised.isEmpty()) {
            throw new CustomReminderInvalidChannelsException();
        }
        normalised.add(CustomReminderChannel.IN_APP);
        return normalised;
    }

    public static void validateTitle(String title, int maxLength) {
        if (title == null || title.trim().isEmpty()) {
            throw new CustomReminderInvalidTitleException("title must not be blank");
        }
        if (title.length() > maxLength) {
            throw new CustomReminderInvalidTitleException("title must be at most " + maxLength + " characters");
        }
    }

    public static void validateNote(String note, int maxLength) {
        if (note != null && note.length() > maxLength) {
            throw new CustomReminderInvalidTitleException("note must be at most " + maxLength + " characters");
        }
    }
}
