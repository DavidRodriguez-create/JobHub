-- 048-auth-drop-admin-trigger-action.sql
--
-- Story #384 / sub-issue #388 -- admin crawl/enrichment trigger gated by the admin's own
-- 2FA (ADR 0019), superseding ADR 0003's emailed request-code gate.
--
-- Narrow auth.verification_code.action back to ('verify-email', 'delete-account',
-- 'delete-all-applications'), removing 'admin-trigger' (reverses 023). The admin
-- crawl/enrichment trigger no longer uses an emailed verification code at all; it is
-- gated by the triggering admin's own TOTP 2FA, verified service-to-service via the new
-- /internal/users/{userId}/two-factor and /internal/two-factor/verify endpoints.
--
-- Forward-only, additive-then-narrowing migration. Prod runs Hibernate with `validate`,
-- so this file is the source of truth for the schema it must validate against. Dev/test
-- build the schema from the JPA entities via drop-and-create and do not apply this file.
--
-- Must delete any residual 'admin-trigger' rows first: they are short-lived (15-minute
-- TTL per auth.verification.code-ttl-seconds) so this delete is always safe, and the
-- re-added narrower CHECK would otherwise fail on any leftover row.
--
-- Mount this file in podman-compose.yml and podman-compose.native.yml after
-- 047-pg-stat-statements.sql.

DELETE FROM auth.verification_code WHERE action = 'admin-trigger';

ALTER TABLE auth.verification_code
    DROP CONSTRAINT IF EXISTS verification_code_action_check;

ALTER TABLE auth.verification_code
    ADD CONSTRAINT verification_code_action_check
    CHECK (action IN ('verify-email', 'delete-account', 'delete-all-applications'));
