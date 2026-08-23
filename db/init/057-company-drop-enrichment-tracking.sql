-- 057-company-drop-enrichment-tracking.sql
-- Story #484: drop automatic company-enrichment tracking (053) now that the automatic
-- LLM-inference slice is removed (ADR 0026 superseded). Company data comes from the 054
-- curated seed plus the admin manual-edit path. Forward-only. crawler.job_post.enriched_at
-- is a DIFFERENT column on a DIFFERENT table and is deliberately untouched.
DROP INDEX IF EXISTS crawler.idx_company_enrich_pending;
ALTER TABLE crawler.company DROP COLUMN IF EXISTS enriched_at;
ALTER TABLE crawler.company DROP COLUMN IF EXISTS enrichment_attempts;
