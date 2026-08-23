-- 041-notification-notifications.sql
-- Notification service schema: in-app notification center (Story #79).

CREATE TABLE notification.notifications (
    -- Identity
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary — references auth.user.id)
    user_id         UUID        NOT NULL,

    -- Notification content
    type            VARCHAR(50)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    message         TEXT         NOT NULL,
    read            BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id_created_at ON notification.notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_id_read ON notification.notifications (user_id, read);

CREATE OR REPLACE FUNCTION notification.trg_notifications_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_before_update
    BEFORE UPDATE ON notification.notifications
    FOR EACH ROW EXECUTE FUNCTION notification.trg_notifications_updated();
