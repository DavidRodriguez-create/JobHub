-- Runs before Hibernate on the DevServices Postgres container.
CREATE SCHEMA IF NOT EXISTS notification;

-- Mirrors db/init/041-notification-notifications.sql (Story #79)
-- + 044-notification-add-application-id.sql (Story #82).
CREATE TABLE notification.notifications (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    message         TEXT         NOT NULL,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    application_id  UUID        NULL
);

CREATE INDEX idx_notifications_user_id_created_at ON notification.notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_id_read ON notification.notifications (user_id, read);
CREATE INDEX idx_notifications_application_id ON notification.notifications (application_id) WHERE application_id IS NOT NULL;

-- notification.digest_run (db/init/042-notification-digest-run.sql) is Hibernate-managed here
-- via drop-and-create from DigestRunEntity, same as notification_preferences.

-- Interview reminder sent log (Story #81 / migration 043).
-- Not Hibernate-managed (no entity registered as @Table), so created here explicitly.
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

-- Custom reminders (Story #134 / migration 046). Not Hibernate-managed for the same
-- reason as interview_reminder_sent above: CHECK constraints Hibernate's
-- drop-and-create DDL doesn't generate.
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
