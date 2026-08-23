-- test-seeds.sql — most component tests register through the API.
ALTER TABLE auth.user ALTER COLUMN first_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN last_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN email TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN password_hash TYPE TEXT;

-- Fixed-id seed users for GET /internal/users/emails (ticket #100, ADR 0008).
-- f0000000-...-0099 is intentionally NOT seeded (used to test silent omission
-- of non-existent users).
INSERT INTO auth.user (id, first_name, last_name, email, password_hash, email_verified, email_verified_at, two_factor_enabled, created_at, updated_at)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'Verified', 'One', 'verified1@example.com', 'x', TRUE, NOW(), FALSE, NOW(), NOW()),
    ('f0000000-0000-0000-0000-000000000002', 'Verified', 'Two', 'verified2@example.com', 'x', TRUE, NOW(), FALSE, NOW(), NOW()),
    ('f0000000-0000-0000-0000-000000000003', 'Unverified', 'Three', 'unverified@example.com', 'x', FALSE, NULL, FALSE, NOW(), NOW());
