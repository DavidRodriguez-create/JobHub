
-- 018-crawler-trigger-cancel.sql
-- Story #58 / Issue #70 (ADR 0006): cooperative cancellation for admin-triggered
-- crawl and enrichment passes.
--
-- Widens the status CHECK to include the two new states and grants job_user
-- UPDATE so the cancel endpoint on job-service can transition active rows.

-- 1. Drop and recreate the status CHECK to add cancel_requested + cancelled.
ALTER TABLE crawler.trigger_request
    DROP CONSTRAINT IF EXISTS trigger_request_status_check;

ALTER TABLE crawler.trigger_request
    ADD CONSTRAINT chk_trigger_request_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed',
                          'cancel_requested', 'cancelled'));

-- 2. Grant UPDATE to job_user so job-service can set cancel_requested / cancelled.
--    job_user already has SELECT + INSERT (016); this adds UPDATE for the cancel flow.
GRANT UPDATE ON crawler.trigger_request TO job_user;
