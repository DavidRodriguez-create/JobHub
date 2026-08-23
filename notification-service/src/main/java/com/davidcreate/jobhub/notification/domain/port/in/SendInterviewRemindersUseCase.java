package com.davidcreate.jobhub.notification.domain.port.in;

public interface SendInterviewRemindersUseCase {

    /**
     * Runs one interview-reminder pass: fetches upcoming next steps from
     * application-service, computes H24/H1 fire instants, and for each eligible
     * (user, application, offset) not yet recorded as sent, creates the in-app
     * notification and (per preferences) sends the reminder email, then records the
     * send in {@code interview_reminder_sent}. Never throws - per-combination failures
     * are logged and skipped, not propagated (AC-19/BR-8).
     */
    void run();
}
