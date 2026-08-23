package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;

import java.util.UUID;

public interface InterviewReminderSentRepository {

    /**
     * @return {@code true} if a {@code interview_reminder_sent} row already exists for the
     *         given {@code (userId, applicationId, reminderOffset)} tuple (BR-4).
     */
    boolean exists(UUID userId, UUID applicationId, ReminderOffset reminderOffset);

    InterviewReminderSent save(InterviewReminderSent interviewReminderSent);
}
