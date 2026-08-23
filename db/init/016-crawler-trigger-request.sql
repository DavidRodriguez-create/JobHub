
-- 016-crawler-trigger-request.sql
-- Story #7 (ADR 0003): admin-triggered crawl/enrichment control table.
-- job-service inserts `queued` rows; crawler-service's TriggerRequestScheduler
-- polls, claims, and runs them through to a terminal state.

CREATE TABLE crawler.trigger_request (
    -- Identity
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- What to run
    kind            VARCHAR(32)  NOT NULL
                        CHECK (kind IN ('crawl', 'enrichment')),

    -- State machine
    status          VARCHAR(32)  NOT NULL DEFAULT 'queued'
                        CHECK (status IN ('queued', 'running', 'succeeded', 'failed')),

    -- Audit (admin user UUID from the JWT subject; no FK across schemas)
    requested_by    UUID,
    requested_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,

    -- Outcome
    result_summary  TEXT,
    error_reason    TEXT,

    -- Lease — mirrors crawler.pull_target so multiple crawler instances
    -- don't double-claim the same request.
    locked_by       VARCHAR(255),
    lease_expires_at TIMESTAMPTZ
);

-- Poll index: the scheduler claims the oldest queued row per kind.
CREATE INDEX idx_trigger_request_poll ON crawler.trigger_request (kind, status, requested_at)
    WHERE status = 'queued';


-- ─────────────────────────────────────────
-- Cross-schema grants for job-service (job_user)
-- job-service inserts new trigger requests and reads back status/last-run
-- info; crawler-service (crawler_user) owns the schema and claims/updates rows.
-- ─────────────────────────────────────────

GRANT SELECT, INSERT ON crawler.trigger_request TO job_user;
