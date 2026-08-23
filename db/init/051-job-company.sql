-- 051-job-company.sql
-- Story #428 (ADR 0023): company as a first-class entity.
--
-- Creates crawler.company (owned/written/modelled by job-service, exactly like
-- crawler.saved_job/saved_filter/trigger_request already are - see ADR 0023 D1), links
-- crawler.pull_target.company_id to it, and backfills every existing pull target so no
-- existing job posting ever loses its company name.
--
-- The slug expression used by the backfill below (crawler.tmp_company_slugify) is a SQL
-- mirror of the canonical Java rule (job-service domain/model/CompanySlug.java, ADR 0023
-- D3), good enough for the data that exists today (all current pull_target.company_name
-- values are Latin script; unaccent() covers the diacritic-stripping step). The Java rule
-- stays canonical for every target the scheduled reconciler resolves afterward - a
-- divergence between the two can at worst create a duplicate company row for a future
-- target, never lose or rename an existing company (ADR 0023 D6 note).

CREATE TABLE crawler.company (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             TEXT         NOT NULL,
    name             TEXT         NOT NULL,
    website          TEXT,
    industry         TEXT,
    size             TEXT,
    headquarters     TEXT,
    description      TEXT,
    logo_url         TEXT,
    tags             TEXT[],
    source           VARCHAR(16)  NOT NULL DEFAULT 'crawl',
    manually_edited  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_company_slug        UNIQUE (slug),
    CONSTRAINT chk_company_source     CHECK (source IN ('crawl', 'derived', 'manual')),
    CONSTRAINT chk_company_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- Deliberately NO updated_at trigger here (unlike crawler.pull_target, which has one).
-- job-service is the only writer of crawler.company and sets updated_at itself on every
-- write, so the DevServices drop-and-create test schema behaves identically to prod.

ALTER TABLE crawler.pull_target ADD COLUMN company_id UUID REFERENCES crawler.company(id);
-- No new index: pull_target is ~234 rows and the join uses company's primary key.


-- ── Backfill (one transaction) ───────────────────────────────────────────────────────
BEGIN;

CREATE EXTENSION IF NOT EXISTS unaccent;

-- One-time SQL mirror of CompanySlug.of(...) (S6 delete-chars, S7 unaccent, S8 lower,
-- S9 tokenize, then a small trailing-legal-form strip covering the common single-suffix
-- case). Dropped again at the end of this transaction - it is not a permanent object.
CREATE OR REPLACE FUNCTION crawler.tmp_company_slugify(raw_name TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT NULLIF(
        regexp_replace(
            regexp_replace(
                regexp_replace(
                    lower(unaccent(regexp_replace(raw_name, $regex$[.,'’`´"]$regex$, '', 'g'))),
                    '[^a-z0-9]+', '-', 'g'
                ),
                '(^-+|-+$)', '', 'g'
            ),
            '-(sa|inc|ltd|gmbh|kg|co|corp|plc|nv|bv|se|ag|srl|sl|spa)$', ''
        ),
        ''
    )
$$;

WITH slugged AS (
    SELECT
        id,
        company_name,
        company_logo_url,
        crawler.tmp_company_slugify(company_name) AS slug
    FROM crawler.pull_target
    WHERE company_id IS NULL
),
grouped AS (
    SELECT
        slug,
        -- Deterministic pick: the longest name in the group, tie-broken alphabetically.
        (ARRAY_AGG(company_name ORDER BY length(company_name) DESC, company_name ASC))[1] AS name,
        -- First non-null logo in the group (nulls sort last).
        (ARRAY_AGG(company_logo_url ORDER BY (company_logo_url IS NULL), company_name ASC))[1] AS logo_url
    FROM slugged
    WHERE slug IS NOT NULL
    GROUP BY slug
)
INSERT INTO crawler.company (slug, name, logo_url, source)
SELECT slug, name, logo_url, 'crawl'
FROM grouped
ON CONFLICT (slug) DO NOTHING;

UPDATE crawler.pull_target pt
SET company_id = c.id
FROM crawler.company c
WHERE pt.company_id IS NULL
  AND c.slug = crawler.tmp_company_slugify(pt.company_name);

DROP FUNCTION crawler.tmp_company_slugify(TEXT);

COMMIT;


-- ── Least-privilege grants (the entire grant impact of this story) ──────────────────
GRANT SELECT, INSERT, UPDATE ON crawler.company TO job_user;
GRANT UPDATE (company_id) ON crawler.pull_target TO job_user;
-- No DELETE anywhere: deleting a company would orphan the link; merges are story #430
-- territory. crawler_user picks up DML on crawler.company automatically via the
-- ALTER DEFAULT PRIVILEGES in 001-schemas.sql - unused today, harmless, already true of
-- crawler.saved_job. No change to db/init-users.sh: no new role, schema or password.


-- ── Verification (mechanical check for #437) ─────────────────────────────────────────
SELECT
    (SELECT COUNT(*) FROM crawler.pull_target)                          AS total_pull_targets,
    (SELECT COUNT(*) FROM crawler.pull_target WHERE company_id IS NOT NULL) AS resolved_pull_targets;
