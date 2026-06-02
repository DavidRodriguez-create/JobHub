
-- 020-auth.sql

CREATE TABLE auth.user (
    -- Identity
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Profile
    first_name          TEXT        NOT NULL,
    last_name           TEXT        NOT NULL,
    email               TEXT        NOT NULL,

    -- Auth
    password_hash       TEXT        NOT NULL,
    email_verified      BOOLEAN     NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMPTZ,

    -- Audit
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_email UNIQUE (email)
);

CREATE OR REPLACE FUNCTION auth.trg_user_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_before_update
    BEFORE UPDATE ON auth.user
    FOR EACH ROW EXECUTE FUNCTION auth.trg_user_updated();


-- ─────────────────────────────────────────
-- Email verification tokens
-- One-time opaque tokens delivered by the registration / resend email.
-- Consumed by POST /auth/account/verify-email to flip email_verified.
-- ─────────────────────────────────────────

CREATE TABLE auth.email_verification_token (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    token           TEXT        NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_email_verification_token UNIQUE (token)
);

CREATE INDEX idx_email_verification_token_user ON auth.email_verification_token (user_id);


-- ─────────────────────────────────────────
-- Verification codes
-- Short-lived 6-digit codes (hashed) authorising a destructive action.
-- Issued by POST /auth/account/verifications, consumed by the target endpoint
-- (e.g. DELETE /auth/account) within 15 minutes.
-- ─────────────────────────────────────────

CREATE TABLE auth.verification_code (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    action          VARCHAR(40) NOT NULL
                        CHECK (action IN ('delete-account', 'delete-all-applications')),
    code_hash       TEXT        NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_verification_code_user ON auth.verification_code (user_id);