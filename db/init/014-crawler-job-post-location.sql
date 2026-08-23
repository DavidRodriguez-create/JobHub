
-- 014-crawler-job-post-location.sql
-- Story #1 / Issue #288 (ADR 0017): multiple (country, city) openings per job post.
--
-- Owner: crawler-service (crawler_user owns the crawler schema). job-service reads
-- this table cross-schema (job_user gets SELECT) exactly like crawler.job_post.
--
-- Backward compatibility: crawler.job_post keeps its single `city` / `country` columns
-- as the PRIMARY location (drives the existing single-`location` API string, the
-- content_hash, and the LOWER(city)/LOWER(country) filter indexes from 017). This new
-- child table stores the FULL set of openings; the primary opening is ALSO mirrored here
-- as an is_primary=true row so the child table alone is a complete picture.
--
-- Forward-only. This file's number (014) sorts before the already-applied 015-019 crawler
-- migrations. That is safe on a FRESH volume (all crawler migrations are independent DDL).
-- On an EXISTING volume it will NOT auto-run — apply it by hand, order-independent because
-- every statement is idempotent:
--   podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/014-crawler-job-post-location.sql
-- then restart crawler-service and job-service.


-- ─────────────────────────────────────────
-- Child table: one row per opening of a job post.
-- (country, city) mirror the semantics of crawler.job_post.city/country:
--   * either part may be NULL (country-only opening),
--   * the special value 'Remote' may live in city or country (case-insensitive),
--   * no separate remote flag — matches how single locations are stored today.
-- ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS crawler.job_post_location (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    job_post_id  UUID        NOT NULL
                     REFERENCES crawler.job_post(id) ON DELETE CASCADE,

    country      TEXT,
    city         TEXT,

    -- Exactly one row per post is the primary; it mirrors job_post.city/country and
    -- drives the single-`location` API string. Uniqueness enforced by the partial index below.
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Stable display ordering; primary first (ordering handled in the query, not stored).
    position     SMALLINT    NOT NULL DEFAULT 0,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- A country-only, city-only or fully-null-but-distinct opening is allowed, but the same
    -- (post, country, city) pair must not be duplicated. NULLs are treated as equal so a
    -- post cannot carry two (NULL country, NULL city) rows.
    CONSTRAINT uq_job_post_location_post_country_city
        UNIQUE NULLS NOT DISTINCT (job_post_id, country, city)
);

-- At most one primary opening per post.
CREATE UNIQUE INDEX IF NOT EXISTS uq_job_post_location_one_primary
    ON crawler.job_post_location (job_post_id)
    WHERE is_primary;

-- Fetch all openings for a post (detail view / response mapping).
CREATE INDEX IF NOT EXISTS idx_job_post_location_post
    ON crawler.job_post_location (job_post_id);

-- Serve the location filter's EXISTS/JOIN match and the country facet aggregation
-- (case-insensitive, same shape as 017's LOWER(city)/LOWER(country) on job_post).
CREATE INDEX IF NOT EXISTS idx_job_post_location_lower_country
    ON crawler.job_post_location (LOWER(country));

CREATE INDEX IF NOT EXISTS idx_job_post_location_lower_city
    ON crawler.job_post_location (LOWER(city));


-- ─────────────────────────────────────────
-- Backfill: seed the child table from the existing single location on every job_post,
-- as the primary opening. Idempotent (skips posts that already have a primary row).
-- Posts with no city AND no country get no child row (nothing to represent).
-- ─────────────────────────────────────────

INSERT INTO crawler.job_post_location (job_post_id, country, city, is_primary, position)
SELECT jp.id, jp.country, jp.city, TRUE, 0
FROM crawler.job_post jp
WHERE (jp.city IS NOT NULL OR jp.country IS NOT NULL)
  AND NOT EXISTS (
      SELECT 1 FROM crawler.job_post_location l
      WHERE l.job_post_id = jp.id AND l.is_primary
  );


-- ─────────────────────────────────────────
-- Cross-schema grant for job-service (job_user reads postings; read-only, like job_post).
-- ─────────────────────────────────────────

GRANT SELECT ON crawler.job_post_location TO job_user;
