-- Story #52 (ADR 0005): attach the FTS trigger to crawler.job_post.
-- The trigger function crawler.trg_job_post_search_vector() is created in
-- init-test.sql (schema-level, survives Hibernate's drop-and-create). Hibernate
-- creates crawler.job_post fresh on every test run, so the trigger itself must be
-- (re-)attached here, after table creation but before the INSERTs below — this
-- makes search_vector populate for every seed row via the same BEFORE INSERT path
-- used in production (db/init/017-job-post-perf.sql).
DROP TRIGGER IF EXISTS trg_job_post_search_vector ON crawler.job_post;
CREATE TRIGGER trg_job_post_search_vector
    BEFORE INSERT OR UPDATE OF title, description
    ON crawler.job_post
    FOR EACH ROW
    EXECUTE FUNCTION crawler.trg_job_post_search_vector();

-- Baseline fixtures for job-service component tests.
-- Hibernate (drop-and-create) provisions the schema first using the JPA entity
-- definitions in this service, then runs this script. We seed three pull_target
-- rows (referenced by job_post.target_id) plus job_post rows that the
-- component-test assertions depend on.
--
-- Row layout (11 rows total; rows 1-7 unchanged in ID/content from before Story #1):
--   1  Stripe   / Madrid    / Spain   / full-time  / English+Spanish / 70k-90k  / senior
--   2  Stripe   / Barcelona / Spain   / full-time  / English         / 60k-80k  / mid
--   3  Stripe   / Madrid    / Spain   / contract   / English         / NULL      / junior  ← unpriced
--   4  Spotify  / Berlin    / Germany / full-time  / English+German  / 65k-85k  / senior
--   5  Spotify  / Madrid    / Spain   / full-time  / English         / 80k-100k / lead
--   6  Stripe   / Madrid    / Spain   / full-time  / English         / 90k-110k / senior  ← first_seen_at = NOW() (recent)
--   7  Stripe   / NULL      / Remote  / full-time  / English         / NULL      / junior  ← BE-F18: Remote bucket (primary)
--   8  Stripe   / Barcelona,Spain (primary) + Amsterdam,Netherlands (additional)  ← Story #1 multi-opening (2 countries)
--   9  Stripe   / Barcelona,Spain (primary) + Madrid,Spain (additional)          ← Story #1 same-country, two cities
--  10  Spotify  / Berlin,Germany (primary) + Remote (additional)                 ← Story #1 Remote as additional opening
--  11  Northwind Freight / NULL / NULL, NO job_post_location child row           ← Story #319 zero-location edge (TC-319-JOB-*)
--  12  Nestlé S.A. (greenhouse) / NULL / NULL, NO job_post_location child row    ← Story #428 merge pair (QAE-428-C-04)
--  13  NESTLE SA (lever)        / NULL / NULL, NO job_post_location child row    ← Story #428 merge pair (QAE-428-C-04)
--  14  Acme Only (workday)      / NULL / NULL, NO job_post_location child row    ← Story #428 name-only company (QAE-428-C-05)
--
-- Story #1 (ADR 0017) seed-row-8-layout lock: rows 8/9/10 are kept as three DISTINCT
-- postings (not folded into one row) per the QAE doc's recommended layout — this keeps
-- FACET-2 (Netherlands isolation) and FACET-3 (two distinct Remote contributors: row 7
-- primary + row 10 additional) each traceable to exactly one seed row. Row 8 uses the
-- Stripe pull_target so FACET-5's company-narrowing assertion (location=Netherlands ->
-- companies == {Stripe: 1}) is unambiguous.
--
-- Every job_post row 1-7 additionally gets ONE mirrored primary child row in
-- crawler.job_post_location (see the INSERT block below) so FILTER/FACET component tests
-- exercise the "child table present" path uniformly — this is also the regression proof
-- that primary-only postings behave identically once the child table exists (ADR 0017).
--
-- Story #319 row 11: a brand-new third company (Northwind Freight, NOT Stripe/Spotify) so
-- the hardcoded Stripe=7 / Spotify=3 facet assertions in JobFacetsDrillDownComponentTest
-- stay unchanged. city/country NULL, no job_post_location child row at all — proves the
-- zero-location posting is excluded from both the location filter and the locations facet
-- (TC-319-JOB-RETURN-01, TC-319-JOB-FILTER-03, TC-319-JOB-FACET-01). Title/keywords avoid
-- every literal used elsewhere ("Java", "Developer", "Senior", "fintech", "Python",
-- "pipelines", "automating") so FTS keyword-count assertions are unaffected; only the
-- UNFILTERED totals shift from 10 to 11 (see JobResourceComponentTest's comment).
--
-- Table-wide totals (11 postings, 13 crawler.job_post_location rows):
--   companies   : Stripe=7, Spotify=3, Northwind Freight=1 (row 11, no dedicated assertion)
--   locations   : Spain=7, Germany=2, Netherlands=1, Remote=2 (synthetic bucket) — unchanged
--     by row 11, which contributes to none of them (BR-319-6)
--     facet-count sum = 7+2+1+2 = 12 (> 11 total postings, expected per AC-1-FACET-4/AC-319-FACET-4 —
--     the QAE doc's own worked example states "= 13" in one spot, which is an arithmetic
--     slip against its own per-country figures; 12 is the correct sum of 7/2/1/2 and is
--     what JobLocationsComponentTest#facetSumExceedsTotalPostingCount asserts)
--   languages   : English=7, Spanish=1, German=1 (rows 8/9/10/11 carry no languages)
--   employmentTypes: full-time=9, contract=1 (row 11 has NULL employment type, no bucket)
--   careerLevels: junior=2, mid=1, senior=3, lead=1, (rows 8/9/10/11 have NULL career level)
--   compensationRange: min=60000, max=110000  (rows 3, 7, 8, 9, 10, 11 have NULL comp, don't affect range)
--   unpriced    : 6 (rows 3, 7, 8, 9, 10, 11)
--   recent (today): 1 (row 6)

-- Story #428 (ADR 0023): crawler.company rows, inserted BEFORE pull_target so its
-- company_id FK is satisfiable. Hibernate's drop-and-create builds this table from
-- CompanyEntity - no db/init/ script runs in this test profile (see the migration's own
-- dedicated test instead, section H of the QAE doc).
INSERT INTO crawler.company (id, slug, name, website, industry, size, headquarters, description, logo_url, tags, source, manually_edited, created_at, updated_at)
VALUES
    ('c1111111-c111-c111-c111-c11111111111', 'stripe', 'Stripe',
        'https://stripe.com', 'Fintech', '5001-10000', 'San Francisco, United States',
        'Financial infrastructure for the internet.', 'https://example.com/logos/stripe.png',
        NULL, 'crawl', FALSE, '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    ('c2222222-c222-c222-c222-c22222222222', 'spotify', 'Spotify',
        NULL, 'Music Streaming', '10000+', NULL,
        NULL, NULL, -- deliberately NULL logo: QAE-428-C-07 "null logo, otherwise resolved"
        NULL, 'crawl', FALSE, '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    ('c3333333-c333-c333-c333-c33333333333', 'nestle', 'Nestle',
        NULL, 'Food & Beverage', '10000+', 'Vevey, Switzerland',
        NULL, 'https://example.com/logos/nestle.png',
        NULL, 'crawl', FALSE, '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    ('c4444444-c444-c444-c444-c44444444444', 'acme-only', 'Acme Only',
        NULL, NULL, NULL, NULL, NULL, NULL, -- QAE-428-C-05 "name-only company"
        NULL, 'crawl', FALSE, '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z');

-- Row 1 (Stripe) and row 2 (Spotify) are pre-resolved to their crawler.company row above.
-- Row 3 (Northwind Freight) is deliberately left with company_id NULL: QAE-428-C-06, the
-- transitional fallback case (a target the reconciler has not yet resolved).
INSERT INTO crawler.pull_target (id, source_type, company_name, company_logo_url, company_id)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'greenhouse', 'Stripe',  'https://example.com/logos/stripe.png', 'c1111111-c111-c111-c111-c11111111111'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'lever',      'Spotify', NULL, 'c2222222-c222-c222-c222-c22222222222'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'workday',    'Northwind Freight', NULL, NULL);

-- Story #428: the merge pair (two sources, same employer via slug identity, QAE-428-C-04)
-- and the name-only company's pull target (QAE-428-C-05). The Nestlé-source greenhouse
-- target below also carries a DECOY, deliberately different, non-null company_logo_url:
-- once resolved, the response must show the company's REAL logo_url, never this decoy
-- (QAE-428-C-08).
INSERT INTO crawler.pull_target (id, source_type, company_name, company_logo_url, company_id)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'greenhouse', 'Nestlé S.A.', 'https://example.com/logos/DECOY-should-not-appear.png', 'c3333333-c333-c333-c333-c33333333333'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'lever',      'NESTLE SA',   NULL, 'c3333333-c333-c333-c333-c33333333333'),
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'workday',    'Acme Only',   NULL, 'c4444444-c444-c444-c444-c44444444444');

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
        career_level,
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
        'senior',
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
        'mid',
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
        'junior',
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
        'senior',
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
        'lead',
        ARRAY['English'],
        ARRAY['Java','Kubernetes'],
        '2024-05-01T10:00:00Z',
        '2024-05-02T10:00:00Z'
    ),
    (
        '66666666-6666-6666-6666-666666666666',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Java DevOps Engineer',
        'https://example.com/jobs/java-4',
        'CI/CD and infrastructure automation',
        'hash-java-4',
        'Madrid',
        'Spain',
        90000,
        110000,
        'full-time',
        'senior',
        ARRAY['English'],
        ARRAY['Java','Docker'],
        NOW(),
        NOW()
    ),
    (
        '77777777-7777-7777-7777-777777777777',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Remote DevOps Engineer',
        'https://example.com/jobs/remote-1',
        'Remote infrastructure automation role',
        'hash-remote-1',
        NULL,
        'Remote',
        NULL,
        NULL,
        'full-time',
        'junior',
        ARRAY['English'],
        ARRAY['Terraform','Ansible'],
        '2024-06-01T10:00:00Z',
        '2024-06-02T10:00:00Z'
    ),
    (
        '88888888-8888-8888-8888-888888888888',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Multi-Location Backend Engineer',
        'https://example.com/jobs/multi-1',
        'Backend role open simultaneously in Spain and the Netherlands',
        'hash-multi-1',
        'Barcelona',
        'Spain',
        NULL,
        NULL,
        'full-time',
        NULL,
        ARRAY[]::text[],
        ARRAY[]::text[],
        '2024-07-01T10:00:00Z',
        '2024-07-02T10:00:00Z'
    ),
    (
        '99999999-9999-9999-9999-999999999999',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'Same-Country Two-City Role',
        'https://example.com/jobs/multi-2',
        'Backend role open in both Barcelona and Madrid, Spain',
        'hash-multi-2',
        'Barcelona',
        'Spain',
        NULL,
        NULL,
        'full-time',
        NULL,
        ARRAY[]::text[],
        ARRAY[]::text[],
        '2024-07-03T10:00:00Z',
        '2024-07-04T10:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000010',
        'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
        'Distributed Systems Engineer',
        'https://example.com/jobs/multi-3',
        'Berlin-based role with an additional fully-remote opening',
        'hash-multi-3',
        'Berlin',
        'Germany',
        NULL,
        NULL,
        'full-time',
        NULL,
        ARRAY[]::text[],
        ARRAY[]::text[],
        '2024-07-05T10:00:00Z',
        '2024-07-06T10:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000011',
        'cccccccc-cccc-cccc-cccc-cccccccccccc',
        'Contract Logistics Specialist',
        'https://example.com/jobs/multi-4',
        'Freight coordination role, no fixed office location on file',
        'hash-multi-4',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        ARRAY[]::text[],
        ARRAY[]::text[],
        '2024-07-07T10:00:00Z',
        '2024-07-08T10:00:00Z'
    ),
    -- Story #428: rows 12/13/14 mirror row 11's zero-signal shape exactly (NULL
    -- city/country/compensation/employmentType/careerLevel, empty languages/requirements,
    -- no crawler.job_post_location child row) so they contribute to NO location/keyword/
    -- employment/career-level/compensation facet or filter assertion elsewhere in the
    -- suite. Row 12 (Nestlé S.A., greenhouse) and row 13 (NESTLE SA, lever) back
    -- QAE-428-C-04; row 14 (Acme Only, workday) backs QAE-428-C-05.
    (
        '00000000-0000-0000-0000-000000000012',
        'dddddddd-dddd-dddd-dddd-dddddddddddd',
        'Regional Nutrition Program Analyst',
        'https://example.com/jobs/company-1',
        'Supports nutrition labeling compliance across product lines.',
        'hash-company-1',
        NULL, NULL, NULL, NULL, NULL, NULL,
        ARRAY[]::text[], ARRAY[]::text[],
        '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000013',
        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        'Consumer Goods Brand Analyst',
        'https://example.com/jobs/company-2',
        'Analyzes brand performance metrics for packaged goods.',
        'hash-company-2',
        NULL, NULL, NULL, NULL, NULL, NULL,
        ARRAY[]::text[], ARRAY[]::text[],
        '2026-07-02T10:00:00Z', '2026-07-02T10:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000014',
        'ffffffff-ffff-ffff-ffff-ffffffffffff',
        'General Office Administrator',
        'https://example.com/jobs/company-3',
        'Handles day-to-day administrative tasks for a small office.',
        'hash-company-3',
        NULL, NULL, NULL, NULL, NULL, NULL,
        ARRAY[]::text[], ARRAY[]::text[],
        '2026-07-03T10:00:00Z', '2026-07-03T10:00:00Z'
    );

-- Story #1 (ADR 0017): mirror every existing row's primary location into
-- crawler.job_post_location, then add the multi-opening rows for 8/9/10. Hibernate's
-- drop-and-create builds this table from JobPostLocationEntity before this script runs.
INSERT INTO crawler.job_post_location (job_post_id, country, city, is_primary, position)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Spain',       'Madrid',     TRUE, 0),
    ('22222222-2222-2222-2222-222222222222', 'Spain',       'Barcelona',  TRUE, 0),
    ('33333333-3333-3333-3333-333333333333', 'Spain',       'Madrid',     TRUE, 0),
    ('44444444-4444-4444-4444-444444444444', 'Germany',     'Berlin',     TRUE, 0),
    ('55555555-5555-5555-5555-555555555555', 'Spain',       'Madrid',     TRUE, 0),
    ('66666666-6666-6666-6666-666666666666', 'Spain',       'Madrid',     TRUE, 0),
    ('77777777-7777-7777-7777-777777777777', 'Remote',      NULL,         TRUE, 0),
    -- Row 8: primary Barcelona/Spain + additional Amsterdam/Netherlands (2 openings).
    ('88888888-8888-8888-8888-888888888888', 'Spain',       'Barcelona',  TRUE,  0),
    ('88888888-8888-8888-8888-888888888888', 'Netherlands', 'Amsterdam',  FALSE, 1),
    -- Row 9: primary Barcelona/Spain + additional Madrid/Spain (same country, 2 cities).
    ('99999999-9999-9999-9999-999999999999', 'Spain',       'Barcelona',  TRUE,  0),
    ('99999999-9999-9999-9999-999999999999', 'Spain',       'Madrid',     FALSE, 1),
    -- Row 10: primary Berlin/Germany + additional Remote opening.
    ('00000000-0000-0000-0000-000000000010', 'Germany',     'Berlin',     TRUE,  0),
    ('00000000-0000-0000-0000-000000000010', 'Remote',      NULL,         FALSE, 1);

-- Story #7 (ADR 0003): admin-triggered crawl/enrichment control table.
-- `crawl` is intentionally seeded with NO rows so the happy-path 202 tests
-- (J-C-05) see "never triggered" and dedupe tests (J-C-07/08) can create their
-- own `queued` row via POST without colliding with pre-existing fixtures.
--
-- `enrichment` gets two historical rows so the status endpoint can exercise
-- "most-recent-per-kind" (J-C-23/24/25): an older `failed` run and a more
-- recent `succeeded` run. job-service itself never writes `running`/
-- `succeeded`/`failed` (job_user has only SELECT+INSERT in prod) — these rows
-- simulate what crawler-service would have written.
INSERT INTO crawler.trigger_request (
    id, kind, status, requested_by, requested_at, started_at, finished_at,
    result_summary, error_reason
) VALUES
    (
        '99999999-0000-0000-0000-000000000001',
        'enrichment',
        'failed',
        '10000000-0000-0000-0000-000000000099',
        '2026-06-01T08:00:00Z',
        '2026-06-01T08:00:05Z',
        '2026-06-01T08:00:30Z',
        NULL,
        'Ollama enrichment service unreachable'
    ),
    (
        '99999999-0000-0000-0000-000000000002',
        'enrichment',
        'succeeded',
        '10000000-0000-0000-0000-000000000099',
        '2026-06-02T08:00:00Z',
        '2026-06-02T08:00:05Z',
        '2026-06-02T08:05:00Z',
        'enriched 12 postings',
        NULL
    );

-- Story #430 (ADR 0025, QAE-430 section 0): admin company-enrichment fixture, additive to
-- the four crawler.company rows above (stripe/spotify/nestle/acme-only). Nine dedicated
-- rows so N (the shared baseline company total every browse case asserts against) = 13.
-- Every PUT-mutating case gets its OWN row (mirrors AdminTriggerResourceComponentTest's
-- precedent) so no two write cases can ever touch the same seed row.
INSERT INTO crawler.company (id, slug, name, website, industry, size, headquarters, description, logo_url, tags, source, manually_edited, created_at, updated_at)
VALUES
    -- AC-430-04: the q=strip co-match alongside the existing "Stripe".
    ('43000000-0000-0000-0000-000000000011', 'striped-media', 'Striped Media',
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- AC-430-05/06: the "already curated" side of the manuallyEdited filter.
    ('43000000-0000-0000-0000-000000000012', 'curated-alpha', 'Curated Alpha Co',
        'https://curated-alpha.example.com', 'SaaS', '201-500', 'Lisbon, Portugal',
        'A pre-curated fixture company for the enrichment backlog filter.',
        'https://example.com/logos/curated-alpha.png',
        ARRAY['b2b', 'saas'], 'manual', TRUE,
        '2026-06-01T00:00:00Z', '2026-06-15T00:00:00Z'),
    -- QAE-430-U-01 (AC-430-13): single-field-intent edit, industry/headquarters both NULL.
    ('43000000-0000-0000-0000-000000000013', 'edit-target-co', 'Edit Target Co',
        'https://edit-target.example.com', NULL, '11-50', NULL,
        'A fixture company awaiting its first industry/headquarters edit.',
        'https://example.com/logos/edit-target.png',
        NULL, 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- QAE-430-U-03 (AC-430-15): full-replace clear-on-omit, website populated pre-edit.
    ('43000000-0000-0000-0000-000000000014', 'clear-target-co', 'Clear Target Co',
        'https://clear-target.example.com', 'Logistics', '51-200', 'Rotterdam, Netherlands',
        'A fixture company whose website will be cleared by omission.',
        NULL, NULL, 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- QAE-430-U-04 (AC-430-16): no-op resubmission, every field populated pre-edit.
    ('43000000-0000-0000-0000-000000000015', 'noop-target-co', 'Noop Target Co',
        'https://noop-target.example.com', 'Retail', '1001-5000', 'Dublin, Ireland',
        'A fixture company for the exact-resubmission no-op case.',
        'https://example.com/logos/noop-target.png',
        ARRAY['retail', 'ecommerce'], 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- QAE-430-U-05/U-11 (AC-430-17/40): id/slug/name immutability, repeated edits.
    ('43000000-0000-0000-0000-000000000016', 'immutable-target-co', 'Immutable Target Co',
        'https://immutable-target.example.com', 'Healthcare', '501-1000', 'Zurich, Switzerland',
        NULL, NULL, NULL, 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- QAE-430-U-08 (AC-430-37): tags:[] clears an existing tag array to NULL, not [].
    ('43000000-0000-0000-0000-000000000017', 'tags-clear-co', 'Tags Clear Co',
        NULL, NULL, NULL, NULL, NULL, NULL,
        ARRAY['a', 'b'], 'manual', TRUE,
        '2026-06-01T00:00:00Z', '2026-06-10T00:00:00Z'),
    -- Section E validation cases (AC-430-31..36): shared baseline, never mutated by a
    -- passing 400 test since every 400 case must leave it untouched.
    ('43000000-0000-0000-0000-000000000018', 'valid-target-co', 'Valid Target Co',
        'https://valid-target.example.com', 'Education', '11-50', 'Porto, Portugal',
        'A fixture company used only as the shared validation baseline.',
        'https://example.com/logos/valid-target.png',
        ARRAY['edtech', 'b2c'], 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    -- QAE-430-T-01/02 (AC-430-27/28): tags curated then read back on every posting. The
    -- COMPANY row lives here (additive, safe: nothing hardcodes a total company count
    -- beyond AdminCompanyBrowseComponentTest's own N=13), but its two job_post rows do
    -- NOT: several pre-existing tests (JobResourceComponentTest et al.) hardcode the
    -- unfiltered job-posting totalElements/X-Total-Count against this shared baseline, so
    -- adding job_post rows here would silently break them. Its pull_target + two job_post
    -- rows are instead seeded in AdminCompanyTagsOnPostingComponentTest's own isolated
    -- @TestProfile container (mirrors AdminCompanyOverrideComponentTest / story #429's
    -- CompanyLogoResolutionComponentTest precedent).
    ('43000000-0000-0000-0000-000000000001', 'tags-demo-co', 'Tags Demo Co',
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'crawl', FALSE,
        '2026-06-01T00:00:00Z', '2026-06-01T00:00:00Z');
