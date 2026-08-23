
-- 022-auth-email-verification.sql
--
-- Story #6 / sub-issue #21 — email-CODE account verification.
--
-- Registration now issues a short-lived 6-digit code (hashed) instead of an opaque
-- link token. We reuse the existing auth.verification_code table by adding a new
-- 'verify-email' action, and retire the now-unused auth.email_verification_token table.
--
-- Forward-only migration. Prod runs Hibernate with `validate`, so this file is the
-- source of truth for the schema it must validate against. Dev/test build the schema
-- from the JPA entities via drop-and-create and do not apply this file.
--
-- Mount this file in podman-compose.yml after 021-auth-seeds.sql and before
-- 030-applications.sql (DevOps #26).

-- 1. Allow verification codes to authorise the email-verification flow.
--    The action set was: ('delete-account', 'delete-all-applications').
ALTER TABLE auth.verification_code
    DROP CONSTRAINT IF EXISTS verification_code_action_check;

ALTER TABLE auth.verification_code
    ADD CONSTRAINT verification_code_action_check
    CHECK (action IN ('verify-email', 'delete-account', 'delete-all-applications'));

-- 2. Support the pre-login lookup path. The user is not authenticated when verifying
--    their email, so the code is resolved by (user_id, action) rather than by a
--    client-held verification id. Index the newest unconsumed code per user+action.
CREATE INDEX IF NOT EXISTS idx_verification_code_user_action
    ON auth.verification_code (user_id, action);

-- 3. Retire the opaque-token mechanism. The email_verification_token table and its
--    domain/entity/repository in auth-service are superseded by verification_code with
--    the 'verify-email' action. No production data depends on it (the verification
--    surface is unreleased and nothing seeds this table).
DROP TABLE IF EXISTS auth.email_verification_token;
