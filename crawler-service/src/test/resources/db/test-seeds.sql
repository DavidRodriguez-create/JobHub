-- runs after Hibernate creates tables

ALTER TABLE crawler.job_post ALTER COLUMN description TYPE TEXT;
ALTER TABLE crawler.job_post ALTER COLUMN url TYPE TEXT;
ALTER TABLE crawler.job_post ALTER COLUMN title TYPE VARCHAR(512);

-- Story #1 / #291 (ADR 0017): partial unique index Hibernate DDL generation can't express
-- from annotations (Postgres-only WHERE clause). Mirrors db/init/014-crawler-job-post-location.sql.
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_post_location_one_primary
    ON crawler.job_post_location (job_post_id)
    WHERE is_primary;

-- Story #398 (ADR 0032, N2): same partial unique index as db/init/060 -- Hibernate DDL
-- generation can't express a Postgres-only WHERE clause from annotations.
CREATE UNIQUE INDEX IF NOT EXISTS uq_trigger_request_active_kind_status
    ON crawler.trigger_request (kind, status)
    WHERE status IN ('queued', 'running');

-- active target used for success path
INSERT INTO crawler.pull_target (id, source_type, company_name, token, scraper_config, pull_priority, status, consecutive_failures, next_pull_after, status_changed_at, created_at, updated_at) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-000000000001', 'greenhouse', 'TestCo Alpha', 'testco-alpha', NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('aaaaaaaa-aaaa-aaaa-aaaa-000000000002', 'greenhouse', 'TestCo Beta',  'testco-beta',  NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
-- already locked target used to trigger 409 conflict
('aaaaaaaa-aaaa-aaaa-aaaa-000000000003', 'greenhouse', 'TestCo Gamma', 'testco-gamma', NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW());

UPDATE crawler.pull_target
SET locked_by = 'other-worker',
    lease_expires_at = NOW() + INTERVAL '1 hour'
WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-000000000003';
