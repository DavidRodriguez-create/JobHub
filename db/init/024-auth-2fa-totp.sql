-- 024-auth-2fa-totp.sql
-- TOTP-based two-factor authentication (ADR 0012, story #133 / sub-issue #171).

ALTER TABLE auth.user
    ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- ─────────────────────────────────────────
-- TOTP secret
-- 1:0..1 relationship with auth.user. No row means 2FA is not enabled.
-- A row exists in an unverified state during setup until the first TOTP
-- code is confirmed via POST /account/2fa/verify-setup.
-- ─────────────────────────────────────────

CREATE TABLE auth.totp_secret (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    encrypted_secret    TEXT        NOT NULL,
    verified            BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_totp_secret_user UNIQUE (user_id)
);

-- ─────────────────────────────────────────
-- Two-factor login challenge
-- Short-lived, single-use opaque token issued by step 1 of login (POST
-- /auth/login) when the account has 2FA enabled. Consumed by step 2
-- (POST /auth/login/2fa). Expires after auth.totp.challenge-ttl-minutes.
-- ─────────────────────────────────────────

CREATE TABLE auth.two_factor_challenge (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    token_hash      TEXT        NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_two_factor_challenge_user ON auth.two_factor_challenge (user_id);

-- ─────────────────────────────────────────
-- Backup (recovery) codes
-- Eight single-use codes generated on successful 2FA setup verification,
-- and replaced wholesale on regeneration. Deleted entirely when 2FA is
-- disabled (cascades from totp_secret).
-- ─────────────────────────────────────────

CREATE TABLE auth.totp_backup_code (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    totp_secret_id      UUID        NOT NULL REFERENCES auth.totp_secret(id) ON DELETE CASCADE,
    code_hash           TEXT        NOT NULL,
    consumed_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_totp_backup_code_secret ON auth.totp_backup_code (totp_secret_id);
