-- ─────────────────────────────────────────────────────────────────────────────
-- Story #81 / ticket #109: Interview Reminder Sent log (idempotency table)
-- Schema: notification
-- Migration: 043
-- ─────────────────────────────────────────────────────────────────────────────

-- Interview reminder sent log: one row per (user, application, offset) so the
-- hourly scheduler never sends the same reminder twice (AC-4 / BR-4).
CREATE TABLE notification.interview_reminder_sent (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    application_id   UUID        NOT NULL,
    reminder_offset  TEXT        NOT NULL CHECK (reminder_offset IN ('H24', 'H1')),
    next_step_date   DATE        NOT NULL,
    channels         TEXT,
    sent_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, application_id, reminder_offset)
);

CREATE INDEX idx_interview_reminder_sent_user_id
    ON notification.interview_reminder_sent (user_id);

-- Add the email-channel sub-preference for interview reminders.
-- Default TRUE so existing users continue to receive email reminders (AC-3 / BR-3).
ALTER TABLE notification.notification_preferences
    ADD COLUMN IF NOT EXISTS interview_reminder_email BOOLEAN NOT NULL DEFAULT TRUE;
