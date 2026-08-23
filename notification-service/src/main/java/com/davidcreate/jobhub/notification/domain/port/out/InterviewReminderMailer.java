package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;

public interface InterviewReminderMailer {

    /**
     * Renders and sends an interview-reminder email to the given address.
     *
     * @param recipientEmail destination address
     * @param step           the upcoming next step the reminder is about (label, date,
     *                        company - company may be null, BR-5/AC-13)
     * @param offset          which reminder this is (H24 or H1) - informational for content
     * @throws RuntimeException if rendering or sending fails
     */
    void send(String recipientEmail, UpcomingNextStep step, ReminderOffset offset);
}
