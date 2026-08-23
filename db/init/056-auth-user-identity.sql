-- 056-auth-user-identity.sql
-- Story #459 (ADR 0027): one row per linked social identity. uq_user_identity_provider_subject
-- makes one provider account map to exactly one JobHub user (BR1 step 1's lookup key);
-- uq_user_identity_user_provider caps a user at one identity per provider (BR4).

CREATE TABLE auth.user_identity (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,
    provider          VARCHAR(20) NOT NULL CHECK (provider IN ('google', 'github')),
    provider_user_id  TEXT        NOT NULL,
    email             TEXT,                 -- provider-reported email at link time (audit only)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_identity_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_user_identity_user_provider    UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_identity_user ON auth.user_identity (user_id);

-- Grants: covered by ALTER DEFAULT PRIVILEGES IN SCHEMA auth ... TO auth_user
-- in 001-schemas.sql (matches migrations 022/024/050, no explicit GRANT needed here).
