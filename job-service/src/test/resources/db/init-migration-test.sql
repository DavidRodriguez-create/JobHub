-- Story #428 (QAE-428-MIG-01/02): a schema that mirrors PRE-051 prod exactly -
-- crawler.pull_target with only company_name/company_logo_url, no company_id column,
-- no crawler.company table - so db/init/051-job-company.sql can be executed against it
-- verbatim by CompanyMigrationComponentTest, proving the real file, not a paraphrase.

CREATE SCHEMA IF NOT EXISTS crawler;

-- Minimal prerequisite so 051-job-company.sql's own GRANT statements (its entire
-- least-privilege impact) succeed against this throwaway container - a login-less role
-- is enough for a GRANT target. Verifying the grants actually WORK is explicitly out of
-- scope for this QAE case (devops's mechanical check on #437); this only lets the file
-- run byte-for-byte without erroring on a role db/init-users.sh would have created.
CREATE ROLE job_user;

CREATE TABLE crawler.pull_target (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type       VARCHAR(32)  NOT NULL,
    company_name      VARCHAR(255) NOT NULL,
    company_logo_url  TEXT
);

INSERT INTO crawler.pull_target (id, source_type, company_name, company_logo_url)
VALUES
    -- (a) an ordinary single-employer row
    ('11111111-1111-1111-1111-111111111111', 'greenhouse', 'Acme Corp', 'https://example.com/logos/acme.png'),
    -- (b) two rows that must collapse under the slug rule to ONE company (mirrors the
    -- identical worked pair used at the unit (QAE-428-SLUG-14) and component
    -- (QAE-428-C-04) layers, so a real gap between the SQL mirror and the Java rule
    -- would show up as a THIRD company row instead of one). The first of the pair also
    -- doubles as (c): its company_logo_url is NULL.
    ('22222222-2222-2222-2222-222222222222', 'greenhouse', 'Nestlé S.A.', NULL),
    ('33333333-3333-3333-3333-333333333333', 'lever',      'NESTLE SA',  'https://example.com/logos/nestle.png'),
    -- A standalone row whose own company_logo_url is NULL, unambiguously proving the
    -- resulting company's logo_url stays NULL (not '', not a crash) rather than picking
    -- up a logo from an unrelated group.
    ('44444444-4444-4444-4444-444444444444', 'workday',    'Northwind Freight', NULL);
