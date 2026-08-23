-- src/main/resources/db/dev-seeds.sql
-- runs after Hibernate creates tables

-- fix column types Hibernate won't apply from annotations
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

-- seeds
INSERT INTO crawler.pull_target (id, source_type, company_name, token, scraper_config, pull_priority, status, consecutive_failures, next_pull_after, status_changed_at, created_at, updated_at) VALUES
('b0000000-0000-0000-0001-000000000001', 'greenhouse', 'Stripe',     'stripe',     NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0001-000000000002', 'greenhouse', 'Airbnb',     'airbnb',     NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0001-000000000003', 'greenhouse', 'Datadog',    'datadoghq',  NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0001-000000000004', 'greenhouse', 'Algolia',    'algolia',    NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0001-000000000005', 'greenhouse', 'Doctolib',   'doctolib',   NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0002-000000000001', 'lever',      'Spotify',    'spotify',    NULL, 100, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0002-000000000002', 'lever',      'BlaBlaCar',  'blablacar',  NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0002-000000000003', 'lever',      'Back Market','backmarket', NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0002-000000000004', 'lever',      'Alan',       'alan',       NULL,  90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0002-000000000005', 'lever',      'Pennylane',  'pennylane',  NULL,  85, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0003-000000000001', 'workday',    'Airbus',     NULL, '{"url": "https://ag.wd3.myworkdayjobs.com/Airbus"}',   90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0003-000000000002', 'workday',    'Amadeus',    NULL, '{"url": "https://amadeus.wd3.myworkdayjobs.com/jobs"}', 90, 'active', 0, NOW(), NOW(), NOW(), NOW()),
('b0000000-0000-0000-0004-000000000001', 'amazon',     'Amazon',     NULL, '{"locations": [{"city": "Paris", "country": "FRA"}, {"city": "Madrid", "country": "ESP"}]}', 100, 'active', 0, NOW(), NOW(), NOW(), NOW());