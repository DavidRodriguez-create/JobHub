-- Runs before Hibernate on the DevServices Postgres container.
CREATE SCHEMA IF NOT EXISTS notification;

-- Interview reminder sent log (Story #81 / migration 043).
-- Created here for DevServices; Hibernate validate expects the table to exist.
CREATE TABLE IF NOT EXISTS notification.interview_reminder_sent (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL,
    application_id   UUID        NOT NULL,
    reminder_offset  TEXT        NOT NULL CHECK (reminder_offset IN ('H24', 'H1')),
    next_step_date   DATE        NOT NULL,
    channels         TEXT,
    sent_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, application_id, reminder_offset)
);

CREATE INDEX IF NOT EXISTS idx_interview_reminder_sent_user_id
    ON notification.interview_reminder_sent (user_id);

-- Custom reminders (Story #134 / migration 046).
-- Created here for DevServices; Hibernate validate expects the table to exist.
CREATE TABLE IF NOT EXISTS notification.custom_reminder (
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

CREATE INDEX IF NOT EXISTS idx_custom_reminder_user_status_trigger
    ON notification.custom_reminder (user_id, status, trigger_at_utc);
CREATE INDEX IF NOT EXISTS idx_custom_reminder_user_app
    ON notification.custom_reminder (user_id, application_id);
CREATE INDEX IF NOT EXISTS idx_custom_reminder_due
    ON notification.custom_reminder (status, trigger_at_utc)
    WHERE status = 'SCHEDULED';

CREATE OR REPLACE FUNCTION notification.trg_custom_reminder_updated() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := NOW(); RETURN NEW; END; $$;

DROP TRIGGER IF EXISTS trg_custom_reminder_before_update ON notification.custom_reminder;
CREATE TRIGGER trg_custom_reminder_before_update
    BEFORE UPDATE ON notification.custom_reminder
    FOR EACH ROW EXECUTE FUNCTION notification.trg_custom_reminder_updated();
