-- Story #82 (Ghosted Alert): add nullable application_id column to notification.notifications
-- so ghosted-alert notifications can link back to the originating application.

ALTER TABLE notification.notifications
    ADD COLUMN IF NOT EXISTS application_id UUID;

CREATE INDEX IF NOT EXISTS idx_notifications_application_id
    ON notification.notifications (application_id)
    WHERE application_id IS NOT NULL;
