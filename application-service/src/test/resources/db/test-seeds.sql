-- test-seeds.sql — no rows pre-seeded; tests own their data.
-- Hibernate drop-and-create builds the tables as VARCHAR; widen to TEXT to match prod DDL.
ALTER TABLE applications.user_job_post ALTER COLUMN title TYPE TEXT;
ALTER TABLE applications.user_job_post ALTER COLUMN company TYPE TEXT;
ALTER TABLE applications.user_job_post ALTER COLUMN url TYPE TEXT;
ALTER TABLE applications.user_job_post ALTER COLUMN location TYPE TEXT;

ALTER TABLE applications.job_post_snapshot ALTER COLUMN title TYPE TEXT;
ALTER TABLE applications.job_post_snapshot ALTER COLUMN company TYPE TEXT;
ALTER TABLE applications.job_post_snapshot ALTER COLUMN url TYPE TEXT;
ALTER TABLE applications.job_post_snapshot ALTER COLUMN location TYPE TEXT;
ALTER TABLE applications.job_post_snapshot ALTER COLUMN content_hash TYPE TEXT;

-- ─────────────────────────────────────────────────────────────────────────────
-- Ghosted-alert seed data (GA-APP-* component tests)
--
-- Non-terminal statuses with updated_at = now() - 15 days (stale at threshold=14):
--   AA000001 APPLIED, AA000002 SCREENING, AA000003 INTERVIEWING, AA000004 OFFERED
--
-- Terminal statuses with updated_at = now() - 15 days (must be excluded from stale):
--   CC000001 REJECTED, CC000002 ACCEPTED, CC000003 WITHDRAWN, CC000004 GHOSTED
--
-- Fresh non-terminal with updated_at = now() - 1 day (excluded at threshold >= 2):
--   BB000001 APPLIED
--
-- GA-APP-14 uses a hard-coded UUID for the "already terminal" test:
--   cc000001-0000-0000-0000-000000000001 = REJECTED (already terminal → 409)
--
-- User IDs are arbitrary but stable (not real auth users — internal endpoints have no JWT check).
-- ─────────────────────────────────────────────────────────────────────────────

-- Seed user IDs
-- AA_USER = aa000000-0000-0000-0000-000000000001
-- BB_USER = bb000000-0000-0000-0000-000000000001
-- CC_USER = cc000000-0000-0000-0000-000000000001

-- ── user_job_post entries for stale non-terminal apps ─────────────────────────
INSERT INTO applications.user_job_post (id, user_id, title, company, created_at, updated_at)
VALUES
  ('aa000001-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000001', 'Backend Engineer (applied)', 'Stale Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('aa000002-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000001', 'Backend Engineer (screening)', 'Stale Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('aa000003-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000001', 'Backend Engineer (interviewing)', 'Stale Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('aa000004-0000-0000-0000-000000000001', 'aa000000-0000-0000-0000-000000000001', 'Backend Engineer (offered)', 'Stale Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days');

-- ── user_job_post entries for terminal apps (stale, but must be excluded) ─────
INSERT INTO applications.user_job_post (id, user_id, title, company, created_at, updated_at)
VALUES
  ('cc000001-0000-0000-0000-000000000002', 'cc000000-0000-0000-0000-000000000001', 'Terminal Role (rejected)', 'Gone Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('cc000002-0000-0000-0000-000000000002', 'cc000000-0000-0000-0000-000000000001', 'Terminal Role (accepted)', 'Gone Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('cc000003-0000-0000-0000-000000000002', 'cc000000-0000-0000-0000-000000000001', 'Terminal Role (withdrawn)', 'Gone Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days'),
  ('cc000004-0000-0000-0000-000000000002', 'cc000000-0000-0000-0000-000000000001', 'Terminal Role (ghosted)', 'Gone Corp', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days');

-- ── user_job_post entry for fresh non-terminal app ────────────────────────────
INSERT INTO applications.user_job_post (id, user_id, title, company, created_at, updated_at)
VALUES
  ('bb000001-0000-0000-0000-000000000001', 'bb000000-0000-0000-0000-000000000001', 'Fresh Role (applied)', 'Fresh Inc', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- ── Stale non-terminal applications (15 days since updated_at) ───────────────
INSERT INTO applications.application (id, user_id, user_job_post_id, status, applied_at, created_at, updated_at)
VALUES
  ('aa000001-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000001', 'aa000001-0000-0000-0000-000000000001', 'applied',      NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('aa000002-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000001', 'aa000002-0000-0000-0000-000000000001', 'screening',    NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('aa000003-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000001', 'aa000003-0000-0000-0000-000000000001', 'interviewing', NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('aa000004-0000-0000-0000-000000000002', 'aa000000-0000-0000-0000-000000000001', 'aa000004-0000-0000-0000-000000000001', 'offered',      NOW() - INTERVAL '16 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days');

-- ── Terminal applications (15 days since updated_at, must be excluded) ────────
-- GA-APP-14: cc000001-0000-0000-0000-000000000001 is the specific terminal UUID tested
INSERT INTO applications.application (id, user_id, user_job_post_id, status, applied_at, ended_at, created_at, updated_at)
VALUES
  ('cc000001-0000-0000-0000-000000000001', 'cc000000-0000-0000-0000-000000000001', 'cc000001-0000-0000-0000-000000000002', 'rejected',  NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('cc000002-0000-0000-0000-000000000001', 'cc000000-0000-0000-0000-000000000001', 'cc000002-0000-0000-0000-000000000002', 'accepted',  NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('cc000003-0000-0000-0000-000000000001', 'cc000000-0000-0000-0000-000000000001', 'cc000003-0000-0000-0000-000000000002', 'withdrawn', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days'),
  ('cc000004-0000-0000-0000-000000000001', 'cc000000-0000-0000-0000-000000000001', 'cc000004-0000-0000-0000-000000000002', 'ghosted',   NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days', NOW() - INTERVAL '16 days', NOW() - INTERVAL '15 days');

-- ── Fresh non-terminal application (1 day since updated_at) ──────────────────
INSERT INTO applications.application (id, user_id, user_job_post_id, status, applied_at, created_at, updated_at)
VALUES
  ('bb000001-0000-0000-0000-000000000002', 'bb000000-0000-0000-0000-000000000001', 'bb000001-0000-0000-0000-000000000001', 'applied', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- ─────────────────────────────────────────────────────────────────────────────
-- Ownership-check seed data (OWN-C-001..003 component tests)
--
-- Application f0000000-...-0099 owned by user fa000000-...-0001.
-- OWN-C-001: HEAD with matching userId -> 204
-- OWN-C-002: HEAD with different userId (fa000000-...-0002) -> 404
-- OWN-C-003: HEAD with random UUID not in DB -> 404 (no seed row needed)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO applications.user_job_post (id, user_id, title, company, created_at, updated_at)
VALUES
  ('f0000000-0000-0000-0000-000000000099', 'fa000000-0000-0000-0000-000000000001', 'Ownership Test Role', 'Test Corp', NOW(), NOW());

INSERT INTO applications.application (id, user_id, user_job_post_id, status, applied_at, created_at, updated_at)
VALUES
  ('f0000000-0000-0000-0000-000000000099', 'fa000000-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000099', 'applied', NOW(), NOW(), NOW());
