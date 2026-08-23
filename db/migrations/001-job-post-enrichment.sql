-- 001-job-post-enrichment.sql
--
-- Adds the career-level dimension and LLM-enrichment bookkeeping to
-- crawler.job_post WITHOUT recreating the table or dropping data.
-- Idempotent — safe to run more than once.
--
-- Apply to a running database (data preserved):
--   podman exec -i jobhub-db psql -U jobhub -d jobhub < db/migrations/001-job-post-enrichment.sql
--
-- The matching CREATE TABLE in db/init/010-crawler.sql is kept in sync so a
-- fresh volume gets the same shape.

ALTER TABLE crawler.job_post
    ADD COLUMN IF NOT EXISTS career_level        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS enrichment_status   VARCHAR(16) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS enriched_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS enrichment_attempts SMALLINT    NOT NULL DEFAULT 0;

ALTER TABLE crawler.job_post DROP CONSTRAINT IF EXISTS chk_job_post_career_level;
ALTER TABLE crawler.job_post ADD CONSTRAINT chk_job_post_career_level
    CHECK (career_level IS NULL OR career_level IN
        ('internship', 'junior', 'mid', 'senior', 'lead', 'principal', 'manager', 'director'));

ALTER TABLE crawler.job_post DROP CONSTRAINT IF EXISTS chk_job_post_enrichment_status;
ALTER TABLE crawler.job_post ADD CONSTRAINT chk_job_post_enrichment_status
    CHECK (enrichment_status IN ('pending', 'done', 'failed'));

CREATE INDEX IF NOT EXISTS idx_job_post_career_level
    ON crawler.job_post (career_level);

-- Drives the enrichment scan: cheap lookup of rows still awaiting the model.
CREATE INDEX IF NOT EXISTS idx_job_post_enrichment_pending
    ON crawler.job_post (enrichment_status)
    WHERE enrichment_status = 'pending';
