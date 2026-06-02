
-- 021-auth-seeds.sql

INSERT INTO auth.user (id, first_name, last_name, email, password_hash, email_verified, email_verified_at) VALUES
(
    'a0000000-0000-0000-0000-000000000001',
    'Alice',
    'Martin',
    'alice@example.com',
    '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6hsxq6PZDi', -- password: test1234
    TRUE,
    NOW()
),
(
    'a0000000-0000-0000-0000-000000000002',
    'Bob',
    'Dupont',
    'bob@example.com',
    '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6hsxq6PZDi', -- password: test1234
    TRUE,
    NOW()
),
(
    'a0000000-0000-0000-0000-000000000003',
    'Clara',
    'Nguyen',
    'clara@example.com',
    '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj6hsxq6PZDi', -- password: test1234
    FALSE,
    NULL -- email not verified yet
);