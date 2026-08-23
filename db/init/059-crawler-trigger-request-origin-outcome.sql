
-- 059-crawler-trigger-request-origin-outcome.sql
-- Story #398 (ADR 0032): trigger-run origin (scheduled vs manual) and machine-readable
-- outcome. The scheduled crawl now records its own trigger_request row (N2), so origin
-- distinguishes it from an admin-triggered one; outcome is the terminal result
-- (completed / no_targets / cancelled / interrupted / failed) -- status stays succeeded
-- for no_targets (N1). Pre-existing rows predate origin/outcome: origin defaults (and is
-- backfilled) to 'manual' so the admin panel treats them as manual with no outcome, not
-- as an error; outcome stays NULL for them (no terminal-result data to recover).

ALTER TABLE crawler.trigger_request
    ADD COLUMN origin  VARCHAR(32) NOT NULL DEFAULT 'manual'
        CHECK (origin IN ('scheduled', 'manual')),
    ADD COLUMN outcome VARCHAR(32)
        CHECK (outcome IN ('completed', 'no_targets', 'cancelled', 'interrupted', 'failed'));

-- Explicit backfill for existing rows (belt-and-braces alongside the column DEFAULT above).
UPDATE crawler.trigger_request SET origin = 'manual' WHERE origin IS NULL;

-- No new GRANT needed: db/init/016 already grants job_user SELECT (and INSERT/UPDATE via
-- 016/018) on crawler.trigger_request; PostgreSQL table-level privileges cover columns
-- added later by ALTER TABLE.
