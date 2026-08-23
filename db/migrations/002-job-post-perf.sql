-- 002-job-post-perf.sql
--
-- Forward-only migration for story #52 (ADR 0005): job-post query performance.
-- Adds missing indexes (Tier 1) and full-text search (Tier 2) to an existing
-- database WITHOUT recreating tables or dropping data.
-- Idempotent — safe to run more than once.
--
-- Apply to a running database:
--   podman exec -i jobhub-db psql -U jobhub -d jobhub < db/migrations/002-job-post-perf.sql
--
-- The matching init script db/init/017-job-post-perf.sql is kept in sync so a
-- fresh volume gets the same shape.


-- ═════════════════════════════════════════════════════════════════════════════
-- Tier 1 — Missing indexes
-- ═════════════════════════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS idx_job_post_languages_gin
    ON crawler.job_post USING gin (languages);

CREATE INDEX IF NOT EXISTS idx_job_post_lower_city
    ON crawler.job_post (LOWER(city));

CREATE INDEX IF NOT EXISTS idx_job_post_lower_country
    ON crawler.job_post (LOWER(country));

CREATE INDEX IF NOT EXISTS idx_pull_target_lower_company
    ON crawler.pull_target (LOWER(company_name));

CREATE INDEX IF NOT EXISTS idx_job_post_comp_range
    ON crawler.job_post (compensation_min, compensation_max);


-- ═════════════════════════════════════════════════════════════════════════════
-- Tier 2 — Full-text search (tsvector column + trigger + GIN index)
-- ═════════════════════════════════════════════════════════════════════════════

ALTER TABLE crawler.job_post
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE OR REPLACE FUNCTION crawler.trg_job_post_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_job_post_search_vector ON crawler.job_post;
CREATE TRIGGER trg_job_post_search_vector
    BEFORE INSERT OR UPDATE OF title, description
    ON crawler.job_post
    FOR EACH ROW
    EXECUTE FUNCTION crawler.trg_job_post_search_vector();

-- Backfill existing rows (only those not yet populated).
UPDATE crawler.job_post
SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B')
WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_job_post_search_vector_gin
    ON crawler.job_post USING gin (search_vector);
