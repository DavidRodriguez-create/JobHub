-- 042-notification-digest-run.sql
-- Notification service schema: weekly digest run tracking (Story #80).
--
-- NOTE: ADR 0008 assigns this table to db/init/041-notification-digest-run.sql, but 041 was
-- already taken by 041-notification-notifications.sql (story #79). Using the next free number
-- in the notification range (042) instead.

CREATE TABLE notification.digest_run (
    -- Identity
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary — references auth.user.id)
    user_id         UUID        NOT NULL,

    -- Run outcome
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    job_count       INTEGER     NOT NULL DEFAULT 0,
    status          TEXT        NOT NULL,
    error_message   TEXT,

    CONSTRAINT chk_digest_run_status CHECK (status IN ('sent', 'failed', 'skipped'))
);

CREATE INDEX idx_digest_run_user_id_sent_at ON notification.digest_run (user_id, sent_at DESC);
CREATE INDEX idx_digest_run_user_id_status_sent_at ON notification.digest_run (user_id, status, sent_at DESC);
