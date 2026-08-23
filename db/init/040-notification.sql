-- 040-notification.sql
-- Notification service schema: user notification preferences.

CREATE TABLE notification.notification_preferences (
    -- Identity
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary — references auth.user.id)
    user_id         UUID        NOT NULL,

    -- Preference toggles (defaults match product spec)
    weekly_digest_email             BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_notifications_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    interview_reminders             BOOLEAN NOT NULL DEFAULT TRUE,
    ghosted_alert                   BOOLEAN NOT NULL DEFAULT TRUE,

    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- One preferences row per user
    CONSTRAINT uq_notification_preferences_user_id UNIQUE (user_id)
);

CREATE INDEX idx_notification_preferences_user_id ON notification.notification_preferences (user_id);

CREATE OR REPLACE FUNCTION notification.trg_notification_preferences_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notification_preferences_before_update
    BEFORE UPDATE ON notification.notification_preferences
    FOR EACH ROW EXECUTE FUNCTION notification.trg_notification_preferences_updated();
