-- 053-company-enrichment-tracking.sql
-- Story #354 (ADR 0026 D6): idempotency/churn bookkeeping for automatic company
-- enrichment. job-service remains the sole writer of crawler.company (ADR 0023 D1);
-- these two columns let its /internal/companies/... endpoints run the crawler-driven
-- inference loop at most once per company, and bound transient-failure retries.
--
-- Column names/types mirror crawler.job_post (db/init/010-crawler.sql:140-158) for
-- consistency: enriched_at is stamped when an inference attempt COMPLETES (a
-- parseable answer, even an all-null one); enrichment_attempts counts every attempt,
-- including 'unavailable' ones, and bounds the retry loop.
--
-- No new grant: 051-job-company.sql already granted SELECT, INSERT, UPDATE ON
-- crawler.company TO job_user at table scope, which covers these two new columns.
-- No backfill: existing rows get enriched_at = NULL, enrichment_attempts = 0, which is
-- exactly "pending" - the reconciler picks them up on its own via the new index below.

ALTER TABLE crawler.company ADD COLUMN enriched_at         TIMESTAMPTZ;
ALTER TABLE crawler.company ADD COLUMN enrichment_attempts SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX idx_company_enrich_pending ON crawler.company (enriched_at)
    WHERE enriched_at IS NULL AND manually_edited = false;
