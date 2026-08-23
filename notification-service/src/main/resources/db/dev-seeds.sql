-- Dev seed data for notification-service.
-- Insert sample preferences for a known test user.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, ghosted_alert, created_at, updated_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', true, false, true, true, now(), now());
