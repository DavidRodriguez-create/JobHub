-- ─────────────────────────────────────────────────────────────────────────────
-- Story #134: Custom reminders on a job application
-- Schema: notification
-- Migration: 046
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE notification.custom_reminder (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    application_id  UUID        NOT NULL,
    title           VARCHAR(200) NOT NULL,
    note            TEXT,
    trigger_at_utc  TIMESTAMPTZ NOT NULL,
    channels        TEXT        NOT NULL,
    stage           VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    channels_fired  TEXT,
    fired_at_utc    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_custom_reminder_status
        CHECK (status IN ('SCHEDULED', 'FIRED', 'CANCELLED')),
    CONSTRAINT chk_custom_reminder_stage
        CHECK (stage IS NULL OR stage IN ('SCREENING', 'INTERVIEW', 'OFFER')),
    CONSTRAINT chk_custom_reminder_channels_nonempty
        CHECK (length(trim(channels)) > 0)
);

CREATE INDEX idx_custom_reminder_user_status_trigger
    ON notification.custom_reminder (user_id, status, trigger_at_utc);
CREATE INDEX idx_custom_reminder_user_app
    ON notification.custom_reminder (user_id, application_id);
CREATE INDEX idx_custom_reminder_due
    ON notification.custom_reminder (status, trigger_at_utc)
    WHERE status = 'SCHEDULED';

CREATE OR REPLACE FUNCTION notification.trg_custom_reminder_updated() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := NOW(); RETURN NEW; END; $$;

CREATE TRIGGER trg_custom_reminder_before_update
    BEFORE UPDATE ON notification.custom_reminder
    FOR EACH ROW EXECUTE FUNCTION notification.trg_custom_reminder_updated();
