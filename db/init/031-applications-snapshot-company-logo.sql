-- 031-applications-snapshot-company-logo.sql
-- Story #244 (ADR 0015): freeze the real company logo URL (and confirm company name capture)
-- on the application's job-post snapshot at apply time. Nullable: manual entries, source
-- posts without a logo, and pre-existing snapshots have no value (no backfill, by design).

ALTER TABLE applications.job_post_snapshot
    ADD COLUMN company_logo_url TEXT;
