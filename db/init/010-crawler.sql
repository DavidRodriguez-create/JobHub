
-- 010-crawler.sql

CREATE TABLE crawler.pull_target (
    -- Identity
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Target definition
    source_type                 VARCHAR(32)  NOT NULL,
    company_name                VARCHAR(255) NOT NULL,
    company_logo_url            TEXT,                   -- absolute URL of the company logo (CompanyInfo.logoUrl)
    token                       VARCHAR(255),           -- for simple slug-based scrapers (greenhouse, lever)
    scraper_config              JSONB,                  -- for complex scrapers (workday, amazon)

    

    -- Scheduling
    pull_priority               SMALLINT     NOT NULL DEFAULT 100,
    next_pull_after             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- State machine
    status                      VARCHAR(32)  NOT NULL DEFAULT 'active'
                                    CHECK (status IN (
                                        'active',
                                        'cooldown',
                                        'disabled_transient',
                                        'disabled_permanent'
                                    )),
    status_reason               TEXT,
    status_changed_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Locking
    locked_by                   VARCHAR(255),
    lease_expires_at            TIMESTAMPTZ,

    -- Pull history
    last_successful_pull        TIMESTAMPTZ,
    last_pull_attempt           TIMESTAMPTZ,
    consecutive_failures        SMALLINT     NOT NULL DEFAULT 0,

    -- Audit
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_pull_target_source_token UNIQUE (source_type, token),
    CONSTRAINT chk_token_or_config CHECK (token IS NOT NULL OR scraper_config IS NOT NULL)
);

CREATE INDEX idx_pull_target_scheduler ON crawler.pull_target (pull_priority DESC, next_pull_after)
    WHERE status = 'active' AND locked_by IS NULL;

CREATE INDEX idx_pull_target_source_type ON crawler.pull_target (source_type)
    WHERE status = 'active';

CREATE OR REPLACE FUNCTION crawler.trg_pull_target_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_changed_at := NOW();
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pull_target_before_update
    BEFORE UPDATE ON crawler.pull_target
    FOR EACH ROW EXECUTE FUNCTION crawler.trg_pull_target_updated();


-- ─────────────────────────────────────────

CREATE TABLE crawler.pull_log (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    target_id           UUID        NOT NULL REFERENCES crawler.pull_target(id),
    pulled_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success             BOOLEAN     NOT NULL,
    http_status         SMALLINT,
    error_reason        TEXT,
    duration_ms         INTEGER
);

CREATE INDEX idx_pull_log_target_id ON crawler.pull_log (target_id, pulled_at DESC);


-- ─────────────────────────────────────────

CREATE TABLE crawler.job_post (
    -- Identity
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Source
    target_id           UUID        NOT NULL REFERENCES crawler.pull_target(id),

    -- Content
    title               TEXT        NOT NULL,
    url                 TEXT        NOT NULL,
    description         TEXT,
    -- hash(lower(trim(title)) || '|' || lower(trim(company_name)) || '|' || lower(trim(city)) || '|' || lower(trim(description)))
    -- normalize before hashing: lowercase, trim, collapse whitespace
    -- purpose: dedup cross-site posts that have same content but different URLs
    content_hash        TEXT,

    -- Location
    city                TEXT,
    country             TEXT,

    -- Compensation (annual, local currency units)
    compensation_min    INTEGER,
    compensation_max    INTEGER,

    -- Role attributes
    employment_type     VARCHAR(32)
                            CHECK (employment_type IN (
                                'full-time',
                                'part-time',
                                'contract',
                                'freelance',
                                'internship'
                            )),
    languages           TEXT[],   -- working languages, e.g. {'English','Spanish'}
    requirements        TEXT[],   -- structured list of role requirements

    -- Audit
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_job_post_url  UNIQUE (url),
    CONSTRAINT uq_job_post_hash UNIQUE (content_hash)
);

CREATE INDEX idx_job_post_target_id        ON crawler.job_post (target_id);
CREATE INDEX idx_job_post_country_city     ON crawler.job_post (country, city);
CREATE INDEX idx_job_post_first_seen_at    ON crawler.job_post (first_seen_at DESC);
CREATE INDEX idx_job_post_compensation_min ON crawler.job_post (compensation_min);
CREATE INDEX idx_job_post_employment_type  ON crawler.job_post (employment_type);


-- ─────────────────────────────────────────
-- Saved jobs — per-user bookmarks (owned by job-service)
-- ─────────────────────────────────────────

CREATE TABLE crawler.saved_job (
    -- Identity
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary to auth.user)
    user_id     UUID        NOT NULL,

    -- Bookmarked posting
    job_id      UUID        NOT NULL REFERENCES crawler.job_post(id) ON DELETE CASCADE,

    saved_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_saved_job_user_job UNIQUE (user_id, job_id)
);

CREATE INDEX idx_saved_job_user ON crawler.saved_job (user_id, saved_at DESC);


-- ─────────────────────────────────────────
-- Saved filters — named job-search presets (max 5 per user, enforced in service)
-- ─────────────────────────────────────────

CREATE TABLE crawler.saved_filter (
    -- Identity
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary to auth.user)
    user_id     UUID        NOT NULL,

    -- Content
    name        TEXT        NOT NULL,
    -- serialised FilterValues JSON (field names mirror the GET /jobs query params)
    filters     TEXT        NOT NULL,

    -- Audit
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saved_filter_user ON crawler.saved_filter (user_id);