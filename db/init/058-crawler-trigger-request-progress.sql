
-- 058-crawler-trigger-request-progress.sql
-- Story #513 (ADR 0029): live crawl-run progress on the existing crawler.trigger_request row.
-- Nine nullable columns, no new table, no new default. progress_updated_at IS NULL is the
-- single, unambiguous "this run has never reported progress" marker (queued run, enrichment
-- run, or a run that predates this feature); it is distinct from progress_new_posts = 0
-- ("reported, and nothing new yet"). crawler-service (crawler_user) is the sole writer, via a
-- REQUIRES_NEW-transaction CrawlProgressRecorder; job-service (job_user) maps these read-only
-- (insertable = false, updatable = false) so its own cancel-path UPDATE can never clobber the
-- live counters crawler-service is advancing concurrently.

ALTER TABLE crawler.trigger_request
    ADD COLUMN progress_targets_visited     INTEGER,
    ADD COLUMN progress_new_posts           INTEGER,
    ADD COLUMN progress_current_company     TEXT,
    ADD COLUMN progress_current_source_type VARCHAR(64),
    ADD COLUMN progress_last_company        TEXT,
    ADD COLUMN progress_last_source_type    VARCHAR(64),
    ADD COLUMN progress_last_found_posts    INTEGER,
    ADD COLUMN progress_last_new_posts      INTEGER,
    ADD COLUMN progress_updated_at          TIMESTAMPTZ;

-- No new GRANT needed: db/init/016 grants SELECT, INSERT and db/init/018 grants UPDATE on
-- crawler.trigger_request to job_user; PostgreSQL table-level privileges automatically cover
-- columns added later by ALTER TABLE, so job-service's existing grants already reach these
-- nine columns. Verified against 016/018 before writing this comment (ADR 0029 decision 3).
