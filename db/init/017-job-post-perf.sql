
-- 017-job-post-perf.sql
-- Story #52 / Issue #64 (ADR 0005): job-post query performance improvements.
--
-- Tier 1 — missing indexes for expression-based filters.
-- Tier 2 — full-text search column (tsvector) + trigger + GIN index.
--
-- Runs as the postgres superuser via docker-entrypoint-initdb.d on first
-- volume creation.  On an existing volume, apply by hand:
--   podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/017-job-post-perf.sql
--
-- All statements are idempotent (IF NOT EXISTS / OR REPLACE).


-- ═════════════════════════════════════════════════════════════════════════════
-- Tier 1 — Missing indexes
-- ═════════════════════════════════════════════════════════════════════════════

-- 1. GIN on languages array — serves  array_overlaps(jp.languages, :languages)
CREATE INDEX IF NOT EXISTS idx_job_post_languages_gin
    ON crawler.job_post USING gin (languages);

-- 2. Functional B-tree on LOWER(city) — serves  LOWER(j.city) = :loc
CREATE INDEX IF NOT EXISTS idx_job_post_lower_city
    ON crawler.job_post (LOWER(city));

-- 3. Functional B-tree on LOWER(country) — serves  LOWER(j.country) = :loc
CREATE INDEX IF NOT EXISTS idx_job_post_lower_country
    ON crawler.job_post (LOWER(country));

-- 4. Functional B-tree on LOWER(company_name) on pull_target — serves
--    LOWER(t.companyName) IN :companies
CREATE INDEX IF NOT EXISTS idx_pull_target_lower_company
    ON crawler.pull_target (LOWER(company_name));

-- 5. Composite B-tree on (compensation_min, compensation_max) — serves
--    range queries that filter on both bounds.  The existing single-column
--    idx_job_post_compensation_min is kept (used by ORDER BY salary_asc/desc).
CREATE INDEX IF NOT EXISTS idx_job_post_comp_range
    ON crawler.job_post (compensation_min, compensation_max);


-- ═════════════════════════════════════════════════════════════════════════════
-- Tier 2 — Full-text search (tsvector column + trigger + GIN index)
-- ═════════════════════════════════════════════════════════════════════════════

-- 1. Add the tsvector column (nullable — NULLs are filled by the backfill below)
ALTER TABLE crawler.job_post
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- 2. Trigger function: rebuild search_vector when title or description changes.
--    Uses 'english' text-search configuration.  coalesce() ensures NULL
--    descriptions don't discard the title vector.
CREATE OR REPLACE FUNCTION crawler.trg_job_post_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$;

-- 3. Trigger on INSERT and UPDATE of title/description.
--    DROP first so re-running the script doesn't fail on duplicate trigger name.
DROP TRIGGER IF EXISTS trg_job_post_search_vector ON crawler.job_post;
CREATE TRIGGER trg_job_post_search_vector
    BEFORE INSERT OR UPDATE OF title, description
    ON crawler.job_post
    FOR EACH ROW
    EXECUTE FUNCTION crawler.trg_job_post_search_vector();

-- 4. Backfill existing rows.
UPDATE crawler.job_post
SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B')
WHERE search_vector IS NULL;

-- 5. GIN index on the tsvector column — serves  search_vector @@ tsquery
CREATE INDEX IF NOT EXISTS idx_job_post_search_vector_gin
    ON crawler.job_post USING gin (search_vector);
