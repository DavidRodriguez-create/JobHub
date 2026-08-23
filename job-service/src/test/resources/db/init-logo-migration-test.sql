-- Story #429 (QAE-429-MIG-01..06): a schema that mirrors POST-051 prod so
-- db/init/052-company-logo-backfill.sql can be executed against it verbatim, without
-- 051's OWN backfill inserting any company rows we don't control. crawler.pull_target
-- is intentionally seeded with ZERO rows: 051's backfill only ever touches
-- pull_target rows (see db/init/051-job-company.sql), so an empty table means 051
-- creates crawler.company (and its grants) but inserts nothing into it - leaving the
-- table empty for CompanyLogoBackfillMigrationComponentTest to seed its own
-- five-row fixture directly before running 052.

CREATE SCHEMA IF NOT EXISTS crawler;

-- Minimal prerequisite so 051's own GRANT statements (its entire least-privilege
-- impact, unchanged by 052 - job_user already has UPDATE from 051) succeed against
-- this throwaway container, mirroring init-migration-test.sql's own precedent.
CREATE ROLE job_user;

CREATE TABLE crawler.pull_target (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type       VARCHAR(32)  NOT NULL,
    company_name      VARCHAR(255) NOT NULL,
    company_logo_url  TEXT
);
