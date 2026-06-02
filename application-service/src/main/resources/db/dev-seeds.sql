-- dev-seeds.sql — minimal sample data for the seeded auth.user ids.
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

INSERT INTO applications.user_job_post (id, user_id, title, company, url, location) VALUES
('b1000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
 'Senior Backend Engineer', 'Acme Corp', 'https://acme.example.com/jobs/1', 'Madrid, Spain'),
('b1000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
 'Platform Engineer', 'Globex', 'https://globex.example.com/jobs/2', 'Barcelona, Spain');

INSERT INTO applications.application
    (id, user_id, user_job_post_id, status, applied_at, notes, next_step_label, next_step_date) VALUES
('c1000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
 'b1000000-0000-0000-0000-000000000001', 'applied', NOW() - INTERVAL '5 days', NULL, NULL, NULL),
('c1000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
 'b1000000-0000-0000-0000-000000000002', 'interviewing', NOW() - INTERVAL '2 days',
 'Reached out to the hiring manager on LinkedIn.', 'Technical interview', CURRENT_DATE + 3);

-- Status-change history for the seeded applications (powers the timeline + reply-time metrics).
INSERT INTO applications.application_timeline (application_id, status, occurred_at) VALUES
('c1000000-0000-0000-0000-000000000001', 'applied',      NOW() - INTERVAL '5 days'),
('c1000000-0000-0000-0000-000000000002', 'applied',      NOW() - INTERVAL '2 days'),
('c1000000-0000-0000-0000-000000000002', 'interviewing', NOW() - INTERVAL '1 day');
