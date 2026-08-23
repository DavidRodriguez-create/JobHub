-- 050-auth-apply-profile.sql
-- Story #336 (ADR 0022): apply-profile answer bank, one row per auth.user,
-- backing GET/PUT /auth/account/apply-profile. Distinct surface from
-- auth.user (story #296); never joined into AccountResponse.

CREATE TABLE auth.apply_profile (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID        NOT NULL REFERENCES auth.user(id) ON DELETE CASCADE,

    work_authorization      TEXT,
    requires_sponsorship    BOOLEAN,
    notice_period           TEXT,
    salary_expectation      TEXT,
    current_location        TEXT,
    willing_to_relocate     BOOLEAN,
    linkedin_url            TEXT,
    github_url              TEXT,
    portfolio_url           TEXT,
    languages               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    room_to_grow            TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_apply_profile_user UNIQUE (user_id),
    CONSTRAINT chk_apply_profile_work_authorization_len CHECK (char_length(work_authorization) <= 200),
    CONSTRAINT chk_apply_profile_notice_period_len CHECK (char_length(notice_period) <= 100),
    CONSTRAINT chk_apply_profile_salary_expectation_len CHECK (char_length(salary_expectation) <= 100),
    CONSTRAINT chk_apply_profile_current_location_len CHECK (char_length(current_location) <= 200),
    CONSTRAINT chk_apply_profile_linkedin_url_len CHECK (char_length(linkedin_url) <= 500),
    CONSTRAINT chk_apply_profile_github_url_len CHECK (char_length(github_url) <= 500),
    CONSTRAINT chk_apply_profile_portfolio_url_len CHECK (char_length(portfolio_url) <= 500),
    CONSTRAINT chk_apply_profile_room_to_grow_len CHECK (char_length(room_to_grow) <= 2000)
);

CREATE TRIGGER trg_apply_profile_before_update
    BEFORE UPDATE ON auth.apply_profile
    FOR EACH ROW EXECUTE FUNCTION auth.trg_user_updated();

-- Grants: covered by ALTER DEFAULT PRIVILEGES IN SCHEMA auth ... TO auth_user
-- in 001-schemas.sql (matches migrations 022/024, no explicit GRANT needed here).
