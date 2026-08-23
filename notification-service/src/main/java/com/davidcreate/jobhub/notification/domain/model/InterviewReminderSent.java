package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class InterviewReminderSent {

    private final UUID id;
    private final UUID userId;
    private final UUID applicationId;
    private final ReminderOffset reminderOffset;
    private final LocalDate nextStepDate;
    private final String channels;
    private final Instant sentAt;
}
