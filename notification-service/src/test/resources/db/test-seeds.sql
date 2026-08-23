-- Test seed data for notification-service component tests.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', true, false, true, true, true, now(), now());

INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', false, true, false, true, false, now(), now());

-- Dedicated to TC-16a (PUT with only an unrecognised field) so it is not affected by
-- TC-11's mutation of b0000000-0000-0000-0000-000000000001 within the same test class.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', true, false, true, true, true, now(), now());

-- ── Notification center seed data (Story #79, TC-B-C-*) ───────────────────────
-- user_id e0000000-...-0001: Pagination/ordering user: 25 notifications,
-- created_at strictly descending (now() - N minutes, N=0..24), mixed type,
-- all read=false except the last two (read=true) so totalElements=25, totalPages=2 @ size=20.
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at)
SELECT
    ('f1000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
    'e0000000-0000-0000-0000-000000000001',
    CASE n % 4
        WHEN 0 THEN 'INTERVIEW_REMINDER'
        WHEN 1 THEN 'GHOSTED_ALERT'
        WHEN 2 THEN 'APPLICATION_UPDATE'
        ELSE 'SYSTEM'
    END,
    'Notification #' || n,
    'Message body for notification #' || n,
    (n >= 23),
    now() - (n || ' minutes')::interval,
    now() - (n || ' minutes')::interval
FROM generate_series(0, 24) AS n;

-- user_id e0000000-...-0002: Read/unread filter user: 5 notifications,
-- 3 unread + 2 read, one of each NotificationType, for TC-B-C-04/05/07/09.
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at)
VALUES
    ('f2000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002', 'INTERVIEW_REMINDER', 'Interview reminder', 'Your interview is tomorrow at 10:00 AM', false, now() - interval '1 minute', now() - interval '1 minute'),
    ('f2000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', 'GHOSTED_ALERT', 'No response in 14 days', 'The employer has not responded in 14 days', false, now() - interval '2 minutes', now() - interval '2 minutes'),
    ('f2000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002', 'APPLICATION_UPDATE', 'Application status changed', 'Your application status changed to Interviewing', false, now() - interval '3 minutes', now() - interval '3 minutes'),
    ('f2000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000002', 'SYSTEM', 'Welcome to JobHub', 'Thanks for joining JobHub', true, now() - interval '4 minutes', now() - interval '4 minutes'),
    ('f2000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000002', 'SYSTEM', 'Profile reminder', 'Complete your profile to get better matches', true, now() - interval '5 minutes', now() - interval '5 minutes');

-- user_id e0000000-...-0003: Empty user: 0 notifications (TC-B-C-08, TC-B-C-10).

-- user_id e0000000-...-0004: Single-notification user (mark-read happy path) -
-- 1 notification, read=false, fixed id for TC-B-C-11/12/13.
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at)
VALUES ('f0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000004', 'INTERVIEW_REMINDER', 'Interview tomorrow', 'Your interview with Acme Corp is scheduled for 10:00 AM', false, now(), now());

-- user_id e0000000-...-0005: Mark-all-as-read user: 4 notifications, 3 unread + 1 read (TC-B-C-14/15).
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at)
VALUES
    ('f5000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000005', 'INTERVIEW_REMINDER', 'Interview reminder', 'Your interview is tomorrow at 10:00 AM', false, now() - interval '1 minute', now() - interval '1 minute'),
    ('f5000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000005', 'GHOSTED_ALERT', 'No response in 14 days', 'The employer has not responded in 14 days', false, now() - interval '2 minutes', now() - interval '2 minutes'),
    ('f5000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000005', 'APPLICATION_UPDATE', 'Application status changed', 'Your application status changed to Interviewing', false, now() - interval '3 minutes', now() - interval '3 minutes'),
    ('f5000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000005', 'SYSTEM', 'Welcome to JobHub', 'Thanks for joining JobHub', true, now() - interval '4 minutes', now() - interval '4 minutes');

-- user_id e0000000-...-0006: Cross-user ownership user: 0 rows of its own (TC-B-C-13).

-- user_id e0000000-...-0007: applicationId deep-link user (#182, TC-B-C-23/24): one
-- application-linked GHOSTED_ALERT (unread) and one null-applicationId SYSTEM (unread).
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at, application_id)
VALUES
    ('f7000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000007', 'GHOSTED_ALERT', '👻 A wild ghost appeared!', 'Your application Backend Developer seems to have disappeared into the hiring void. If you''re still interested in the position, a quick follow-up with the recruiter could bring it back to life. Don''t give up! Your next opportunity might be just around the corner.', false, now() - interval '1 minute', now() - interval '1 minute', 'a7000000-0000-0000-0000-000000000001'),
    ('f7000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000007', 'SYSTEM', 'Welcome to JobHub', 'Thanks for joining JobHub', false, now() - interval '2 minutes', now() - interval '2 minutes', NULL);

-- user_id e0000000-...-0008: enrich-at-read (ADR 0014, story #207, NS-C-02): one
-- application-linked notification whose applicationId the WireMock summaries stub will NOT
-- resolve (simulates a deleted/not-owned/unresolved application).
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at, application_id)
VALUES
    ('f8000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000008', 'GHOSTED_ALERT', '👻 A wild ghost appeared!', 'Your application seems to have disappeared into the hiring void. If you''re still interested in the position, a quick follow-up with the recruiter could bring it back to life. Don''t give up! Your next opportunity might be just around the corner.', false, now() - interval '1 minute', now() - interval '1 minute', 'a8000000-0000-0000-0000-000000000099');

-- user_id e0000000-...-0009: delete-happy-path user (story #206, TC-206-B-04/05): exactly
-- one notification so a successful DELETE leaves the user with zero rows.
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at)
VALUES ('f9000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000009', 'SYSTEM', 'Welcome to JobHub', 'Thanks for joining JobHub', false, now(), now());

-- user_id e0000000-...-0010: USER_ALL_TYPES (story #439, TC-439-17/18/21): exactly one
-- notification of each of the 6 NotificationType values, so a single page exercises the
-- full type set for the category-derivation exhaustive check. Dedicated user so this does
-- not disturb the exact-count assertions on USER_FILTER (TC-B-C-04/05/09) or
-- USER_APPLICATION_LINK (TC-B-C-25).
INSERT INTO notification.notifications (id, user_id, type, title, message, read, created_at, updated_at, application_id)
VALUES
    ('fa000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000010', 'INTERVIEW_REMINDER', 'Interview reminder', 'Your interview is tomorrow at 10:00 AM', false, now() - interval '1 minute', now() - interval '1 minute', 'aa000000-0000-0000-0000-000000000001'),
    ('fa000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000010', 'GHOSTED_ALERT', '👻 A wild ghost appeared!', 'Your application seems to have disappeared into the hiring void.', false, now() - interval '2 minutes', now() - interval '2 minutes', 'aa000000-0000-0000-0000-000000000002'),
    ('fa000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000010', 'APPLICATION_UPDATE', 'Application status changed', 'Your application status changed to Interviewing', false, now() - interval '3 minutes', now() - interval '3 minutes', 'aa000000-0000-0000-0000-000000000003'),
    ('fa000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000010', 'CUSTOM_REMINDER', 'Prep for interview', 'Prep for Acme Corp interview', false, now() - interval '4 minutes', now() - interval '4 minutes', 'aa000000-0000-0000-0000-000000000004'),
    ('fa000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000010', 'SECURITY_RECOMMENDATION', 'Enable Two-Factor Authentication', 'Protect your account with 2FA', false, now() - interval '5 minutes', now() - interval '5 minutes', NULL),
    ('fa000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000010', 'SYSTEM', 'Welcome to JobHub', 'Thanks for joining JobHub', false, now() - interval '6 minutes', now() - interval '6 minutes', NULL);

-- ── Weekly digest scheduler seed data (Story #80, WeeklyDigestSchedulerComponentTest) ──
-- Reuses the e0000000-...-000N user ids from the notification-center seeds above (different
-- table, no collision). See TC-21/TC-22/TC-23/TC-09b/TC-20b/TC-13b.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES
    -- 0001: opted-in, history -> personalised digest (TC-21)
    ('c1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', true, true, true, true, true, now(), now()),
    -- 0002: opted-in, no history -> generic digest (TC-22)
    ('c1000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', true, true, true, true, true, now(), now()),
    -- 0003: opted-out -> not a candidate
    ('c1000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000003', false, true, true, true, true, now(), now()),
    -- 0004: opted-in, already sent this week (digest_run row below) -> not resent (TC-09b)
    ('c1000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000004', true, true, true, true, true, now(), now()),
    -- 0005: opted-in, zero matching jobs -> skipped, not sent (TC-23)
    ('c1000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000005', true, true, true, true, true, now(), now()),
    -- 0006: opted-in, missing from auth email batch (unverified/deleted) -> excluded, not failed (TC-20b)
    ('c1000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000006', true, true, true, true, true, now(), now());

-- 0004 already has a 'sent' digest_run row for the current ISO week (TC-09b).
INSERT INTO notification.digest_run (id, user_id, sent_at, job_count, status)
VALUES ('d1000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000004', now(), 4, 'sent');

-- ── Interview reminder scheduler seed data (Story #81, InterviewReminderSchedulerComponentTest) ──
-- Users dd000000-...-000N. next_step_date values are relative to CURRENT_DATE so tests
-- remain valid regardless of run date (ADR 0009 / test-cases doc "relative-date note").
-- NOTE: prefix dd (hex-valid) avoids collision with existing test-user prefixes
--       (b0=prefs users, c0=TC-08/TC-10 new-user IDs, e0=notification-center users).
--
-- Preferences: ir0001=both channels H24-due, ir0002=both H1-due (H24 already sent),
--              ir0003=master off, ir0004=in-app only, ir0005=H24 re-tick,
--              ir0006=no prefs row (default), ir0007=null company, ir0008=H24 sent (AC-16),
--              ir0009=reschedule (AC-17), ir0010=partial failure X, ir0011=partial failure Y.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES
    -- ir0001: both channels on, H24 due (TC-121, TC-127)
    ('dc000000-0000-0000-0000-000000000001', 'dd000000-0000-0000-0000-000000000001', false, true, true, true, false, now(), now()),
    -- ir0002: both channels on, H1 due (H24 already in interview_reminder_sent below) (TC-122)
    ('dc000000-0000-0000-0000-000000000002', 'dd000000-0000-0000-0000-000000000002', false, true, true, true, false, now(), now()),
    -- ir0003: master off (interviewReminders=false) (TC-125)
    ('dc000000-0000-0000-0000-000000000003', 'dd000000-0000-0000-0000-000000000003', false, true, false, true, false, now(), now()),
    -- ir0004: in-app only (interviewReminderEmail=false) (TC-123, TC-126)
    ('dc000000-0000-0000-0000-000000000004', 'dd000000-0000-0000-0000-000000000004', false, true, true, false, false, now(), now()),
    -- ir0005: both channels, H24 already sent -> re-tick idempotency (TC-124)
    ('dc000000-0000-0000-0000-000000000005', 'dd000000-0000-0000-0000-000000000005', false, true, true, true, false, now(), now()),
    -- ir0006: no prefs row -> default both channels on (TC-128 via empty-items path)
    -- (no insert here; absence of row tests default-prefs logic)
    -- ir0007: null company (TC-129)
    ('dc000000-0000-0000-0000-000000000007', 'dd000000-0000-0000-0000-000000000007', false, true, true, true, false, now(), now()),
    -- ir0008: H24 already sent, item disappears next tick (AC-16, TC-130)
    ('dc000000-0000-0000-0000-000000000008', 'dd000000-0000-0000-0000-000000000008', false, true, true, true, false, now(), now()),
    -- ir0009: reschedule scenario: H24 sent for old date, H1 fires for new date (AC-17, TC-131)
    ('dc000000-0000-0000-0000-000000000009', 'dd000000-0000-0000-0000-000000000009', false, true, true, true, false, now(), now()),
    -- ir0010: partial failure X (AC-19, TC-132/TC-133)
    ('dc000000-0000-0000-0000-000000000010', 'dd000000-0000-0000-0000-000000000010', false, true, true, true, false, now(), now()),
    -- ir0011: partial failure Y (AC-19, TC-132/TC-133)
    ('dc000000-0000-0000-0000-000000000011', 'dd000000-0000-0000-0000-000000000011', false, true, true, true, false, now(), now()),
    -- ir0012 (AC-3 / TC-209-C-03): weeklyDigestEmail=false toggle-independence regression -
    -- digest off, interview reminders + interview email both on, H24-due. Proves the digest
    -- flag's value has zero effect on interview-reminder email delivery (story #209).
    ('dc000000-0000-0000-0000-000000000012', 'dd000000-0000-0000-0000-000000000012', false, true, true, true, false, now(), now());

-- ir0002: H24 already sent (so only H1 fires on next tick).
-- application_id matches the WireMock stub value used in tests.
INSERT INTO notification.interview_reminder_sent (id, user_id, application_id, reminder_offset, next_step_date, channels, sent_at)
VALUES ('db000000-0000-0000-0000-000000000002',
        'dd000000-0000-0000-0000-000000000002',
        'da000000-0000-0000-0000-000000000002',
        'H24',
        CURRENT_DATE,
        'in_app,email',
        now() - interval '23 hours');

-- ir0005: H24 sent -> re-tick should not duplicate.
INSERT INTO notification.interview_reminder_sent (id, user_id, application_id, reminder_offset, next_step_date, channels, sent_at)
VALUES ('db000000-0000-0000-0000-000000000005',
        'dd000000-0000-0000-0000-000000000005',
        'da000000-0000-0000-0000-000000000005',
        'H24',
        CURRENT_DATE + interval '1 day',
        'in_app,email',
        now() - interval '1 hour');

-- ir0008: H24 sent (AC-16 mid-flight: item will not appear in WireMock response).
INSERT INTO notification.interview_reminder_sent (id, user_id, application_id, reminder_offset, next_step_date, channels, sent_at)
VALUES ('db000000-0000-0000-0000-000000000008',
        'dd000000-0000-0000-0000-000000000008',
        'da000000-0000-0000-0000-000000000008',
        'H24',
        CURRENT_DATE,
        'in_app,email',
        now() - interval '23 hours');

-- ir0009: H24 sent for old date (AC-17 reschedule: WireMock returns new date, H1 fires).
INSERT INTO notification.interview_reminder_sent (id, user_id, application_id, reminder_offset, next_step_date, channels, sent_at)
VALUES ('db000000-0000-0000-0000-000000000009',
        'dd000000-0000-0000-0000-000000000009',
        'da000000-0000-0000-0000-000000000009',
        'H24',
        CURRENT_DATE - interval '5 days',
        'in_app,email',
        now() - interval '5 days');

-- ── #153 regression seeds (CR-153-C-001..004): dedicated prefix ee100000-... ───
-- ee100000-...-0001: prefs row with interview_reminder_email = true
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('ee900000-0000-0000-0000-000000000001', 'ee100000-0000-0000-0000-000000000001', true, false, true, true, true, now(), now());

-- ee100000-...-0002: prefs row with interview_reminder_email = false
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('ee900000-0000-0000-0000-000000000002', 'ee100000-0000-0000-0000-000000000002', true, false, true, false, true, now(), now());

-- ── #153 scheduler regression seeds (CR-153-C-010/011): dedicated prefix ee110000-... ──
-- ee110000-...-0001: interviewReminders=true, interviewReminderEmail=true -> mailer invoked
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('ee910000-0000-0000-0000-000000000001', 'ee110000-0000-0000-0000-000000000001', false, true, true, true, false, now(), now());

-- ee110000-...-0002: interviewReminders=true, interviewReminderEmail=false -> mailer NOT invoked
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES ('ee910000-0000-0000-0000-000000000002', 'ee110000-0000-0000-0000-000000000002', false, true, true, false, false, now(), now());

-- ── Ghosted-alert / interview-reminder cross-feature independence (Story #209, AC-6) ──
-- Dedicated prefix ee130000-... avoids collision with dd0000-/ee10-/ee11- already in use.
-- ee130000-...-0001 (TC-209-CG-06a): ghosted_alert=false stacked with NON-default
-- interview/digest values (interview_reminders=false, interview_reminder_email=true,
-- weekly_digest_email=true) to prove GhostedAlertService never reads those three fields.
-- ee130000-...-0002 (TC-209-CG-06b): ghosted_alert=true + interview_reminders=false, to prove
-- disabling interview reminders has zero suppressive effect on the ghosted-alert feature.
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES
    ('ee930000-0000-0000-0000-000000000001', 'ee130000-0000-0000-0000-000000000001', true, true, false, true, false, now(), now()),
    ('ee930000-0000-0000-0000-000000000002', 'ee130000-0000-0000-0000-000000000002', true, true, false, true, true, now(), now());

-- ── Custom reminders seed data (Story #134, CustomReminderResourceComponentTest) ──
-- Reserved prefixes per QAE spec: user ee000000-..., application ea000000-..., reminder ec000000-...
-- triggerAtUtc seeded at NOW + 7 days so the dispatch scheduler (disabled in test profile
-- anyway via notification.custom-reminder.enabled=false) never fires these during a run.
INSERT INTO notification.custom_reminder (id, user_id, application_id, title, note, trigger_at_utc, channels, stage, status, created_at, updated_at)
VALUES
    ('ec000000-0000-0000-0000-000000000001', 'ee000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000001', 'Prep notes A1', 'Bring portfolio', now() + interval '7 days', 'IN_APP', 'INTERVIEW', 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000002', 'ee000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000001', 'Prep notes A2', NULL, now() + interval '8 days', 'IN_APP,EMAIL', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000003', 'ee000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000002', 'Prep notes A3', NULL, now() + interval '9 days', 'IN_APP', 'SCREENING', 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000004', 'ee000000-0000-0000-0000-000000000002', 'ea000000-0000-0000-0000-000000000003', 'User B reminder', NULL, now() + interval '7 days', 'IN_APP', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000005', 'ee000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000001', 'Already fired reminder', NULL, now() - interval '1 hour', 'IN_APP', NULL, 'FIRED', now(), now()),
    ('ec000000-0000-0000-0000-000000000006', 'ee000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000001', 'Cancelled reminder', NULL, now() + interval '7 days', 'IN_APP', NULL, 'CANCELLED', now(), now());

UPDATE notification.custom_reminder SET channels_fired = 'IN_APP', fired_at_utc = now() - interval '1 hour'
WHERE id = 'ec000000-0000-0000-0000-000000000005';

-- ── Custom reminder dispatch scheduler seed data (CustomReminderDispatchSchedulerComponentTest) ──
-- Reserved prefix ee000000-...-001N for dispatch-test users (distinct from the resource-test
-- range 0001..0004 above), application ids ee020000-...-001N (WireMock-only, not validated
-- by the scheduler since ownership is only checked at create time).
INSERT INTO notification.notification_preferences (id, user_id, weekly_digest_email, in_app_notifications_enabled, interview_reminders, interview_reminder_email, ghosted_alert, created_at, updated_at)
VALUES
    ('ee900000-0000-0000-0000-000000000010', 'ee000000-0000-0000-0000-000000000010', true, true, true, true, true, now(), now()),
    ('ee900000-0000-0000-0000-000000000011', 'ee000000-0000-0000-0000-000000000011', true, true, true, false, true, now(), now()),
    ('ee900000-0000-0000-0000-000000000013', 'ee000000-0000-0000-0000-000000000013', true, true, true, true, true, now(), now());
-- ee000000-...-0012: no prefs row -> defaults apply.

INSERT INTO notification.custom_reminder (id, user_id, application_id, title, note, trigger_at_utc, channels, stage, status, created_at, updated_at)
VALUES
    ('ec000000-0000-0000-0000-000000000010', 'ee000000-0000-0000-0000-000000000010', 'ea000000-0000-0000-0000-000000000010', 'Due A', NULL, now() - interval '1 minute', 'IN_APP,EMAIL', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000011', 'ee000000-0000-0000-0000-000000000011', 'ea000000-0000-0000-0000-000000000011', 'Due B', NULL, now() - interval '1 minute', 'IN_APP,EMAIL', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000012', 'ee000000-0000-0000-0000-000000000012', 'ea000000-0000-0000-0000-000000000012', 'Due C', NULL, now() - interval '1 minute', 'IN_APP,EMAIL', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000013', 'ee000000-0000-0000-0000-000000000013', 'ea000000-0000-0000-0000-000000000013', 'Due D', NULL, now() - interval '1 minute', 'IN_APP', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000014', 'ee000000-0000-0000-0000-000000000010', 'ea000000-0000-0000-0000-000000000014', 'Already fired', NULL, now() - interval '1 minute', 'IN_APP', NULL, 'FIRED', now(), now()),
    ('ec000000-0000-0000-0000-000000000015', 'ee000000-0000-0000-0000-000000000011', 'ea000000-0000-0000-0000-000000000015', 'Email only gated', NULL, now() - interval '1 minute', 'EMAIL', NULL, 'SCHEDULED', now(), now()),
    ('ec000000-0000-0000-0000-000000000016', 'ee000000-0000-0000-0000-000000000010', 'ea000000-0000-0000-0000-000000000016', 'Cancelled due', NULL, now() - interval '1 minute', 'IN_APP', NULL, 'CANCELLED', now(), now());
