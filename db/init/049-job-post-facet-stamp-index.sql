
-- 049-job-post-facet-stamp-index.sql
-- Story #332 (ADR 0020): facet-response cache invalidated by a crawl-data
-- generation stamp read from crawler.job_post (job-service).
--
-- CrawlGenerationStamp's stamp read:
--   SELECT COALESCE(EXTRACT(EPOCH FROM GREATEST(MAX(last_seen_at), MAX(enriched_at))) * 1000, 0)
--   FROM crawler.job_post
-- These two indexes make each MAX an index backward-scan rather than a
-- full-table aggregate. Pure DDL, no grant change: job_user already holds
-- SELECT on crawler.job_post (db/init/010-crawler.sql).
--
-- Runs as the postgres superuser via docker-entrypoint-initdb.d on first
-- volume creation. On an existing volume, apply by hand:
--   podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/049-job-post-facet-stamp-index.sql
--
-- Idempotent (IF NOT EXISTS).

CREATE INDEX IF NOT EXISTS idx_job_post_last_seen_at
    ON crawler.job_post (last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_post_enriched_at
    ON crawler.job_post (enriched_at DESC);
