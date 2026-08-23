
-- 023-auth-admin-trigger-action.sql
--
-- Story #7 / sub-issue #31 — admin trigger crawl & enrichment.
--
-- Widen auth.verification_code.action to additionally accept 'admin-trigger'. This
-- action authorises an admin (per the AUTH_ADMIN_EMAILS allowlist, see ADR 0003) to
-- confirm a manual crawl/enrichment trigger via an emailed 6-digit code when the
-- optional code gate (JOBHUB_ADMIN_TRIGGER_REQUIRE_CODE) is enabled. The code is
-- consumed service-to-service by job-service via POST /auth/account/verifications/consume.
--
-- Forward-only, additive migration. Prod runs Hibernate with `validate`, so this file
-- is the source of truth for the schema it must validate against. Dev/test build the
-- schema from the JPA entities via drop-and-create and do not apply this file.
--
-- Mount this file in podman-compose.yml after 022-auth-email-verification.sql.

-- The action set was: ('verify-email', 'delete-account', 'delete-all-applications').
ALTER TABLE auth.verification_code
    DROP CONSTRAINT IF EXISTS verification_code_action_check;

ALTER TABLE auth.verification_code
    ADD CONSTRAINT verification_code_action_check
    CHECK (action IN ('verify-email', 'delete-account', 'delete-all-applications', 'admin-trigger'));
