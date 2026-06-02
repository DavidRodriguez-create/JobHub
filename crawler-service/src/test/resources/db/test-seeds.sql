-- runs after Hibernate creates tables

ALTER TABLE crawler.job_post ALTER COLUMN description TYPE TEXT;
ALTER TABLE crawler.job_post ALTER COLUMN url TYPE TEXT;
ALTER TABLE crawler.job_post ALTER COLUMN title TYPE VARCHAR(512);

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
