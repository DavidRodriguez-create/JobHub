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
