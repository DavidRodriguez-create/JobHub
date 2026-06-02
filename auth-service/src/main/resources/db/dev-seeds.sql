-- dev-seeds.sql — mirrors db/seeds/021-auth-seeds.sql (password: test1234)

ALTER TABLE auth.user ALTER COLUMN first_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN last_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN email TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN password_hash TYPE TEXT;

INSERT INTO auth.user (id, first_name, last_name, email, password_hash, email_verified, email_verified_at) VALUES
('a0000000-0000-0000-0000-000000000001', 'Alice', 'Martin', 'alice@example.com',
 '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE,  NOW()),
('a0000000-0000-0000-0000-000000000002', 'Bob',   'Dupont', 'bob@example.com',
 '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', TRUE,  NOW()),
('a0000000-0000-0000-0000-000000000003', 'Clara', 'Nguyen', 'clara@example.com',
 '$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', FALSE, NULL);
