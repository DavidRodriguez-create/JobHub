
-- 011-crawler-seeds.sql

-- 011-crawler-seeds.sql

INSERT INTO crawler.pull_target (id, source_type, company_name, token, scraper_config, pull_priority) VALUES

-- ─────────────────────────────────────────
-- GREENHOUSE  (token only, no scraper_config)
-- verified: boards-api.greenhouse.io/v1/boards/{token}/jobs
-- ─────────────────────────────────────────

('b0000000-0000-0000-0001-000000000001', 'greenhouse', 'Stripe',            'stripe',           NULL, 100),
('b0000000-0000-0000-0001-000000000002', 'greenhouse', 'Airbnb',            'airbnb',           NULL, 100),
('b0000000-0000-0000-0001-000000000003', 'greenhouse', 'Datadog',           'datadoghq',        NULL, 100),
('b0000000-0000-0000-0001-000000000004', 'greenhouse', 'Twilio',            'twilio',           NULL, 100),
('b0000000-0000-0000-0001-000000000005', 'greenhouse', 'Algolia',           'algolia',          NULL,  90),
('b0000000-0000-0000-0001-000000000006', 'greenhouse', 'Doctolib',          'doctolib',         NULL,  90),
('b0000000-0000-0000-0001-000000000007', 'greenhouse', 'Contentsquare',     'contentsquare',    NULL,  90),
('b0000000-0000-0000-0001-000000000008', 'greenhouse', 'Qonto',             'qonto',            NULL,  90),
('b0000000-0000-0000-0001-000000000009', 'greenhouse', 'Leboncoin',         'leboncoin',        NULL,  90),
('b0000000-0000-0000-0001-000000000010', 'greenhouse', 'Contentful',        'contentful',       NULL,  90),
('b0000000-0000-0000-0001-000000000011', 'greenhouse', 'Vestiaire Collective', 'vestiairecollective', NULL, 85),
('b0000000-0000-0000-0001-000000000012', 'greenhouse', 'Spendesk',          'spendesk',         NULL,  85),
('b0000000-0000-0000-0001-000000000013', 'greenhouse', 'Payfit',            'payfit',           NULL,  85),
('b0000000-0000-0000-0001-000000000014', 'greenhouse', 'Swile',             'swile',            NULL,  85),
('b0000000-0000-0000-0001-000000000015', 'greenhouse', 'Mirakl',            'mirakl',           NULL,  85),
('b0000000-0000-0000-0001-000000000016', 'greenhouse', 'Malt',              'malt',             NULL,  85),
('b0000000-0000-0000-0001-000000000017', 'greenhouse', 'Kyriba',            'kyriba',           NULL,  80),
('b0000000-0000-0000-0001-000000000018', 'greenhouse', 'Databricks',        'databricks',       NULL, 100),
('b0000000-0000-0000-0001-000000000019', 'greenhouse', 'Elastic',           'elastic',          NULL,  95),
('b0000000-0000-0000-0001-000000000020', 'greenhouse', 'Cloudflare',        'cloudflare',       NULL,  95),
('b0000000-0000-0000-0005-000000000001', 'greenhouse', 'Monzo',             'monzo',            NULL, 100),
('b0000000-0000-0000-0005-000000000002', 'greenhouse', 'Wise',              'wise',             NULL, 100),
('b0000000-0000-0000-0005-000000000003', 'greenhouse', 'Checkout.com',      'checkout',         NULL,  95),
('b0000000-0000-0000-0005-000000000004', 'greenhouse', 'Wayve',             'wayve',            NULL,  85),
('b0000000-0000-0000-0005-000000000005', 'greenhouse', 'Thought Machine',   'thoughtmachine',   NULL,  85),

-- ─────────────────────────────────────────
-- LEVER  (token only, no scraper_config)
-- verified: jobs.lever.co/{token}
-- ─────────────────────────────────────────

('b0000000-0000-0000-0002-000000000001', 'lever', 'Spotify',               'spotify',          NULL, 100),
('b0000000-0000-0000-0002-000000000002', 'lever', 'BlaBlaCar',             'blablacar',        NULL,  90),
('b0000000-0000-0000-0002-000000000005', 'lever', 'Pennylane',             'pennylane',        NULL,  85),

('b0000000-0000-0000-0002-000000000009', 'lever', 'Alma',                  'alma',             NULL,  80),
('b0000000-0000-0000-0002-000000000010', 'lever', 'Inato',                 'inato',            NULL,  80),
('b0000000-0000-0000-0002-000000000011', 'lever', 'Jobteaser',             'jobteaser',        NULL,  80),
('b0000000-0000-0000-0002-000000000012', 'lever', 'Descartes Underwriting','descartesunderwriting', NULL, 80),
('b0000000-0000-0000-0002-000000000013', 'lever', 'Yokoy',                 'yokoy',            NULL,  75),
('b0000000-0000-0000-0002-000000000014', 'lever', 'Beekeeper',             'beekeeper',        NULL,  75),
('b0000000-0000-0000-0002-000000000015', 'lever', 'Smallpdf',              'smallpdf',         NULL,  75),
('b0000000-0000-0000-0006-000000000001', 'lever', 'Revolut',      'revolut',      NULL, 100),
('b0000000-0000-0000-0006-000000000002', 'lever', 'Deliveroo',    'deliveroo',    NULL,  95), -- ⚠️ verify: jobs.lever.co/deliveroo
('b0000000-0000-0000-0006-000000000003', 'lever', 'Skyscanner',   'skyscanner',   NULL,  90), -- ⚠️ verify: jobs.lever.co/skyscanner
('b0000000-0000-0000-0006-000000000004', 'lever', 'Darktrace',    'darktrace',    NULL,  90), -- ⚠️ verify: jobs.lever.co/darktrace
('b0000000-0000-0000-0006-000000000005', 'lever', 'Starling Bank', 'starlingbank', NULL,  90), -- ⚠️ verify: jobs.lever.co/starlingbank
('b0000000-0000-0000-0006-000000000006', 'lever', 'Improbable',   'improbable',   NULL,  85), -- ⚠️ verify
('b0000000-0000-0000-0006-000000000007', 'lever', 'Tractable',    'tractable',    NULL,  80), -- ⚠️ verify


-- ─────────────────────────────────────────
-- WORKDAY  (scraper_config with verified URLs, no token)
-- URL pattern: {tenant}.wd{n}.myworkdayjobs.com/{site}/jobs
-- ─────────────────────────────────────────

('b0000000-0000-0000-0003-000000000001', 'workday', 'Airbus',    NULL, '{"url": "https://ag.wd3.myworkdayjobs.com/Airbus"}',                               90),
('b0000000-0000-0000-0003-000000000002', 'workday', 'Amadeus',   NULL, '{"url": "https://amadeus.wd3.myworkdayjobs.com/jobs"}',                            90),
('b0000000-0000-0000-0003-000000000003', 'workday', 'Criteo',    NULL, '{"url": "https://criteo.wd3.myworkdayjobs.com/Criteo_Career_Site"}',               85),
-- ⚠️  tokens below need manual URL verification before running
-- find the real URL by visiting the company careers page and checking network requests for myworkdayjobs.com
('b0000000-0000-0000-0003-000000000004', 'workday', 'SAP',       NULL, '{"url": "https://sap.wd3.myworkdayjobs.com/SAP"}',                                100),
('b0000000-0000-0000-0003-000000000005', 'workday', 'Capgemini', NULL, '{"url": "https://capgemini.wd3.myworkdayjobs.com/Capgemini_Careers"}',              90),
('b0000000-0000-0000-0003-000000000006', 'workday', 'Thales',    NULL, '{"url": "https://thales.wd3.myworkdayjobs.com/Thales_Careers"}',                   90),
('b0000000-0000-0000-0003-000000000007', 'workday', 'Ubisoft',   NULL, '{"url": "https://ubi.wd3.myworkdayjobs.com/Ubisoft_Careers"}',                     85),
('b0000000-0000-0000-0003-000000000008', 'workday', 'BBVA',      NULL, '{"url": "https://bbva.wd3.myworkdayjobs.com/BBVAjobs"}',                           85),
('b0000000-0000-0000-0003-000000000009', 'workday', 'Santander', NULL, '{"url": "https://santander.wd3.myworkdayjobs.com/SantanderCareers"}',               85),
('b0000000-0000-0000-0003-000000000010', 'workday', 'Telefonica', NULL, '{"url": "https://telefonica.wd3.myworkdayjobs.com/Telefonica_Careers"}',           85),
('b0000000-0000-0000-0003-000000000011', 'workday', 'Dassault Systèmes', NULL, '{"url": "https://dassault.wd3.myworkdayjobs.com/DassaultSystemes"}',        90),
('b0000000-0000-0000-0003-000000000012', 'workday', 'Sopra Steria', NULL, '{"url": "https://soprasteria.wd3.myworkdayjobs.com/SopraSteria_Careers"}',       90),
('b0000000-0000-0000-0003-000000000013', 'workday', 'Nestlé',    NULL, '{"url": "https://nestle.wd3.myworkdayjobs.com/Nestle_Careers"}',                   80),
('b0000000-0000-0000-0003-000000000014', 'workday', 'Zurich Insurance', NULL, '{"url": "https://zurich.wd3.myworkdayjobs.com/External"}',                   80),
('b0000000-0000-0000-0007-000000000001', 'workday', 'HSBC',        NULL, '{"url": "https://hsbc.wd3.myworkdayjobs.com/HSBCcareer"}',                        90),
('b0000000-0000-0000-0007-000000000002', 'workday', 'BP',          NULL, '{"url": "https://bp.wd3.myworkdayjobs.com/BP_External_Careers"}',                 85),
('b0000000-0000-0000-0007-000000000003', 'workday', 'Rolls-Royce', NULL, '{"url": "https://rollsroyce.wd3.myworkdayjobs.com/Careers"}',                     85),
('b0000000-0000-0000-0007-000000000004', 'workday', 'BAE Systems', NULL, '{"url": "https://baesystems.wd3.myworkdayjobs.com/Careers"}',                     85),

-- ─────────────────────────────────────────
-- AMAZON  (scraper_config with locations, no token)
-- ─────────────────────────────────────────

('b0000000-0000-0000-0004-000000000001', 'amazon', 'Amazon', NULL, '{
    "locations": [
        {"city": "Paris",        "country": "FRA"},
        {"city": "Madrid",       "country": "ESP"},
        {"city": "Barcelona",    "country": "ESP"},
        {"city": "Zurich",       "country": "CHE"},
        {"city": "Seattle",      "country": "USA"},
        {"city": "New York",     "country": "USA"},
        {"city": "San Francisco","country": "USA"}
    ]
}'::jsonb, 100),

('b0000000-0000-0000-0004-000000000002', 'amazon', 'AWS', NULL, '{
    "locations": [
        {"city": "Paris",        "country": "FRA"},
        {"city": "Madrid",       "country": "ESP"},
        {"city": "Zurich",       "country": "CHE"},
        {"city": "Seattle",      "country": "USA"},
        {"city": "New York",     "country": "USA"}
    ]
}'::jsonb, 100);


-- ─────────────────────────────────────────
-- SAVED FILTERS (demo)
-- A sample search preset for the seeded auth user (a0000000-…-0001).
-- Saved jobs are not seeded: job_post rows are produced by the crawler at runtime.
-- ─────────────────────────────────────────

INSERT INTO crawler.saved_filter (id, user_id, name, filters) VALUES
('c0000000-0000-0000-0001-000000000001',
 'a0000000-0000-0000-0000-000000000001',
 'Remote Java in Spain',
 '{"keyword":"Java","location":["Spain","Remote"],"language":["English"],"employmentType":["full-time"],"sort":"newest"}');