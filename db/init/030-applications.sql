-- 030-applications.sql

CREATE TYPE applications.status AS ENUM (
    'applied',
    'screening',
    'interviewing',
    'offered',
    'rejected',
    'accepted',
    'withdrawn',
    'ghosted'
);


-- ─────────────────────────────────────────

CREATE TABLE applications.job_post_snapshot (
    -- Identity
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Source reference (no FK, cross-schema boundary)
    job_post_id     UUID        NOT NULL,
    -- hash(lower(trim(title)) || '|' || lower(trim(company)) || '|' || lower(trim(location)) || '|' || lower(trim(description)))
    -- same hashing logic as crawler.job_post — reuse snapshot if content unchanged
    content_hash    TEXT        NOT NULL,

    -- Frozen content at time of application.
    -- company is nullable: job-service does not yet expose company (planned field).
    title           TEXT        NOT NULL,
    company         TEXT,
    url             TEXT,
    location        TEXT,

    snapshotted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_snapshot_content_hash UNIQUE (content_hash)
);

CREATE INDEX idx_job_post_snapshot_job_post_id ON applications.job_post_snapshot (job_post_id);


-- ─────────────────────────────────────────

CREATE TABLE applications.user_job_post (
    -- Identity
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary)
    user_id         UUID        NOT NULL,

    -- Content (manually entered job details; only title or url is required at the API layer)
    title           TEXT        NOT NULL,
    company         TEXT,
    url             TEXT,
    location        TEXT,

    -- Audit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_job_post_user_id ON applications.user_job_post (user_id);

CREATE OR REPLACE FUNCTION applications.trg_user_job_post_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_job_post_before_update
    BEFORE UPDATE ON applications.user_job_post
    FOR EACH ROW EXECUTE FUNCTION applications.trg_user_job_post_updated();


-- ─────────────────────────────────────────

CREATE TABLE applications.application (
    -- Identity
    id                      UUID                    PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner (no FK, cross-schema boundary)
    user_id                 UUID                    NOT NULL,

    -- Linked post (exactly one must be set)
    job_post_snapshot_id    UUID                    REFERENCES applications.job_post_snapshot(id) ON DELETE SET NULL,
    user_job_post_id        UUID                    REFERENCES applications.user_job_post(id)     ON DELETE SET NULL,

    -- Originating job-service post id for crawled applications (null for manual entries).
    -- Recorded directly (not via the content-deduped snapshot) so it reliably identifies
    -- THIS application's job post — used to map back to job search and to enforce
    -- one application per user per crawled post.
    job_post_id             UUID,

    -- Status
    status                  applications.status     NOT NULL DEFAULT 'applied',
    applied_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    ended_at                TIMESTAMPTZ,

    -- Detail-view fields
    notes                   TEXT,
    contact                 TEXT,
    portal_url              TEXT,

    -- Next step (upcoming action shown on kanban cards / dashboard coming-up widget)
    next_step_label         TEXT,
    next_step_date          DATE,
    next_step_reminder_at   TIMESTAMPTZ,

    -- Audit
    created_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ             NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_one_post CHECK (
        (job_post_snapshot_id IS NOT NULL)::int + (user_job_post_id IS NOT NULL)::int = 1
    )
);

CREATE INDEX idx_application_user_id      ON applications.application (user_id);
CREATE INDEX idx_application_user_status  ON applications.application (user_id, status);
CREATE INDEX idx_application_snapshot_id  ON applications.application (job_post_snapshot_id)
    WHERE job_post_snapshot_id IS NOT NULL;

-- A user may hold at most one application per crawled job post.
CREATE UNIQUE INDEX uq_application_user_job_post ON applications.application (user_id, job_post_id)
    WHERE job_post_id IS NOT NULL;

CREATE OR REPLACE FUNCTION applications.trg_application_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_application_before_update
    BEFORE UPDATE ON applications.application
    FOR EACH ROW EXECUTE FUNCTION applications.trg_application_updated();


-- ─────────────────────────────────────────

-- Ordered status-change history for an application. One row is appended on creation
-- (status 'applied') and on every PATCH /applications/{id}/status. Powers the
-- detail-view timeline and the reply-time / pass-through dashboard metrics.
CREATE TABLE applications.application_timeline (
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID                NOT NULL REFERENCES applications.application(id) ON DELETE CASCADE,
    status          applications.status NOT NULL,
    occurred_at     TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_application_timeline_app ON applications.application_timeline (application_id, occurred_at);