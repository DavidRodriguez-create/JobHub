-- 061-crawler-trigger-request-revoke-job-user.sql
-- Story #582 (ADR 0033): crawler-service becomes the sole writer of
-- crawler.trigger_request. job-service now queues/cancels a pass through
-- crawler-service's internal /internal/trigger-requests endpoints instead of
-- writing the table cross-schema, so job_user no longer needs INSERT or UPDATE
-- on it. Reverses the grants from db/init/016-crawler-trigger-request.sql:46
-- (INSERT) and db/init/018-crawler-trigger-cancel.sql:20 (UPDATE).
--
-- job_user KEEPS SELECT: the admin status/history panel still reads directly.

REVOKE INSERT, UPDATE ON crawler.trigger_request FROM job_user;
