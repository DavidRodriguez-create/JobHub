-- Baseline fixtures for job-service component tests.
-- Hibernate (drop-and-create) provisions the schema first using the JPA entity
-- definitions in this service, then runs this script. We seed two pull_target
-- rows (referenced by job_post.target_id) plus five job_post rows that the
-- JobResourceComponentTest assertions depend on.

INSERT INTO crawler.pull_target (id, source_type, company_name, company_logo_url)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'greenhouse', 'Stripe',  'https://example.com/logos/stripe.png'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'lever',      'Spotify', NULL);

INSERT INTO
    crawler.job_post (
        id,
        target_id,
        title,
        url,
        description,
        content_hash,
        city,
        country,
        compensation_min,
        compensation_max,
        employment_type,
        languages,
        requirements,
        first_seen_at,
        last_seen_at
    )
VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Senior Java Developer',
        'https://example.com/jobs/java-1',
        'Backend role with Spring and Quarkus',
        'hash-java-1',
        'Madrid',
        'Spain',
        70000,
        90000,
        'full-time',
        ARRAY['English','Spanish'],
        ARRAY['Java','Spring'],
        '2024-01-01T10:00:00Z',
        '2024-01-10T10:00:00Z'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Java Backend Engineer',
        'https://example.com/jobs/java-2',
        'Java developer for fintech',
        'hash-java-2',
        'Barcelona',
        'Spain',
        60000,
        80000,
        'full-time',
        ARRAY['English'],
        ARRAY['Java','Postgres'],
        '2024-02-01T10:00:00Z',
        '2024-02-05T10:00:00Z'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Frontend Developer',
        'https://example.com/jobs/fe-1',
        'React + TypeScript role',
        'hash-fe-1',
        'Madrid',
        'Spain',
        NULL,
        NULL,
        'contract',
        ARRAY['English'],
        ARRAY['React','TypeScript'],
        '2024-03-01T10:00:00Z',
        '2024-03-02T10:00:00Z'
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'Python Data Engineer',
        'https://example.com/jobs/py-1',
        'Data pipelines on AWS',
        'hash-py-1',
        'Berlin',
        'Germany',
        65000,
        85000,
        'full-time',
        ARRAY['English','German'],
        ARRAY['Python','AWS'],
        '2024-04-01T10:00:00Z',
        '2024-04-03T10:00:00Z'
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'Java Cloud Developer',
        'https://example.com/jobs/java-3',
        'Cloud-native services',
        'hash-java-3',
        'Madrid',
        'Spain',
        80000,
        100000,
        'full-time',
        ARRAY['English'],
        ARRAY['Java','Kubernetes'],
        '2024-05-01T10:00:00Z',
        '2024-05-02T10:00:00Z'
    );
