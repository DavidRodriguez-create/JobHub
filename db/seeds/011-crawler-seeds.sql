
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
('b0000000-0000-0000-0007-000000000004', 'workday', 'BAE Systems', NULL, '{"url": "https://baesystems.wd3.myworkdayjobs.com/Careers"}',                     85);

-- ─────────────────────────────────────────
-- Story #269: 168 net-new verified sources (152 Greenhouse + 5 Lever + 11 Workday)
-- Same rows as db/init/019-crawler-sources-expand.sql, appended here for fresh
-- volumes. Separate INSERT statement (does not extend the block above) so the
-- pre-existing multi-row INSERT stays untouched.
-- ─────────────────────────────────────────

INSERT INTO crawler.pull_target (id, source_type, company_name, token, scraper_config, pull_priority) VALUES

-- ─────────────────────────────────────────
-- GREENHOUSE  (story #269, 152 rows)
-- verified: boards-api.greenhouse.io/v1/boards/{token}/jobs
-- ─────────────────────────────────────────

('b0000000-0000-0000-0009-000000000001', 'greenhouse', 'MongoDB', 'mongodb', NULL, 90),
('b0000000-0000-0000-0009-000000000002', 'greenhouse', 'HelloFresh', 'hellofresh', NULL, 90),
('b0000000-0000-0000-0009-000000000003', 'greenhouse', 'On (On Running)', 'onrunning', NULL, 90),
('b0000000-0000-0000-0009-000000000004', 'greenhouse', 'Verkada', 'verkada', NULL, 90),
('b0000000-0000-0000-0009-000000000005', 'greenhouse', 'Toast', 'toast', NULL, 90),
('b0000000-0000-0000-0009-000000000006', 'greenhouse', 'Roblox', 'roblox', NULL, 90),
('b0000000-0000-0000-0009-000000000007', 'greenhouse', 'Celonis', 'celonis', NULL, 90),
('b0000000-0000-0000-0009-000000000008', 'greenhouse', 'Adyen', 'adyen', NULL, 90),
('b0000000-0000-0000-0009-000000000009', 'greenhouse', 'Block (Square)', 'block', NULL, 90),
('b0000000-0000-0000-0009-000000000010', 'greenhouse', 'Pinterest', 'pinterest', NULL, 90),
('b0000000-0000-0000-0009-000000000011', 'greenhouse', 'ClickHouse', 'clickhouse', NULL, 90),
('b0000000-0000-0000-0009-000000000012', 'greenhouse', 'Tide', 'tide', NULL, 85),
('b0000000-0000-0000-0009-000000000013', 'greenhouse', 'Fivetran', 'fivetran', NULL, 85),
('b0000000-0000-0000-0009-000000000014', 'greenhouse', 'Grafana Labs', 'grafanalabs', NULL, 85),
('b0000000-0000-0000-0009-000000000015', 'greenhouse', 'Smartsheet', 'smartsheet', NULL, 85),
('b0000000-0000-0000-0009-000000000016', 'greenhouse', 'SoFi', 'sofi', NULL, 85),
('b0000000-0000-0000-0009-000000000017', 'greenhouse', 'Faire', 'faire', NULL, 85),
('b0000000-0000-0000-0009-000000000018', 'greenhouse', 'Dialpad', 'dialpad', NULL, 85),
('b0000000-0000-0000-0009-000000000019', 'greenhouse', 'Bloomreach', 'bloomreach', NULL, 85),
('b0000000-0000-0000-0009-000000000020', 'greenhouse', 'N26', 'n26', NULL, 85),
('b0000000-0000-0000-0009-000000000021', 'greenhouse', 'Vercel', 'vercel', NULL, 85),
('b0000000-0000-0000-0009-000000000022', 'greenhouse', 'Chime', 'chime', NULL, 85),
('b0000000-0000-0000-0009-000000000023', 'greenhouse', 'Proton', 'proton', NULL, 85),
('b0000000-0000-0000-0009-000000000024', 'greenhouse', 'Fireblocks', 'fireblocks', NULL, 80),
('b0000000-0000-0000-0009-000000000025', 'greenhouse', 'Cabify', 'cabify', NULL, 80),
('b0000000-0000-0000-0009-000000000026', 'greenhouse', 'Neo4j', 'neo4j', NULL, 80),
('b0000000-0000-0000-0009-000000000027', 'greenhouse', 'New Relic', 'newrelic', NULL, 80),
('b0000000-0000-0000-0009-000000000028', 'greenhouse', 'Mercury', 'mercury', NULL, 80),
('b0000000-0000-0000-0009-000000000029', 'greenhouse', 'Temporal', 'temporaltechnologies', NULL, 80),
('b0000000-0000-0000-0009-000000000030', 'greenhouse', 'Catawiki', 'catawiki', NULL, 80),
('b0000000-0000-0000-0009-000000000031', 'greenhouse', 'Checkr', 'checkr', NULL, 80),
('b0000000-0000-0000-0009-000000000032', 'greenhouse', 'Bitpanda', 'bitpanda', NULL, 80),
('b0000000-0000-0000-0009-000000000033', 'greenhouse', 'Carta', 'carta', NULL, 80),
('b0000000-0000-0000-0009-000000000034', 'greenhouse', 'Amplitude', 'amplitude', NULL, 80),
('b0000000-0000-0000-0009-000000000035', 'greenhouse', 'SingleStore', 'singlestore', NULL, 80),
('b0000000-0000-0000-0009-000000000036', 'greenhouse', 'Snorkel AI', 'snorkelai', NULL, 80),
('b0000000-0000-0000-0009-000000000037', 'greenhouse', 'Bird (MessageBird)', 'bird', NULL, 80),
('b0000000-0000-0000-0009-000000000038', 'greenhouse', 'Collibra', 'collibra', NULL, 80),
('b0000000-0000-0000-0009-000000000039', 'greenhouse', 'Dataiku', 'dataiku', NULL, 80),
('b0000000-0000-0000-0009-000000000040', 'greenhouse', 'Raisin', 'raisin', NULL, 80),
('b0000000-0000-0000-0009-000000000041', 'greenhouse', 'Airtable', 'airtable', NULL, 80),
('b0000000-0000-0000-0009-000000000042', 'greenhouse', 'Showpad', 'showpad', NULL, 80),
('b0000000-0000-0000-0009-000000000043', 'greenhouse', 'Mixpanel', 'mixpanel', NULL, 80),
('b0000000-0000-0000-0009-000000000044', 'greenhouse', 'CockroachDB', 'cockroachlabs', NULL, 80),
('b0000000-0000-0000-0009-000000000045', 'greenhouse', 'LaunchDarkly', 'launchdarkly', NULL, 80),
('b0000000-0000-0000-0009-000000000046', 'greenhouse', 'Betterment', 'betterment', NULL, 80),
('b0000000-0000-0000-0009-000000000047', 'greenhouse', 'Marqeta', 'marqeta', NULL, 80),
('b0000000-0000-0000-0009-000000000048', 'greenhouse', 'Coalition', 'coalition', NULL, 80),
('b0000000-0000-0000-0009-000000000049', 'greenhouse', 'Salesloft', 'salesloft', NULL, 80),
('b0000000-0000-0000-0009-000000000050', 'greenhouse', 'Shift Technology', 'shifttechnology', NULL, 80),
('b0000000-0000-0000-0009-000000000051', 'greenhouse', 'VTEX', 'vtex', NULL, 80),
('b0000000-0000-0000-0009-000000000052', 'greenhouse', 'Sumo Logic', 'sumologic', NULL, 75),
('b0000000-0000-0000-0009-000000000053', 'greenhouse', 'PagerDuty', 'pagerduty', NULL, 75),
('b0000000-0000-0000-0009-000000000054', 'greenhouse', 'YugabyteDB', 'yugabyte', NULL, 75),
('b0000000-0000-0000-0009-000000000055', 'greenhouse', 'Gemini', 'gemini', NULL, 75),
('b0000000-0000-0000-0009-000000000056', 'greenhouse', 'commercetools', 'commercetools', NULL, 75),
('b0000000-0000-0000-0009-000000000057', 'greenhouse', 'Webflow', 'webflow', NULL, 75),
('b0000000-0000-0000-0009-000000000058', 'greenhouse', 'Make (Integromat)', 'make', NULL, 75),
('b0000000-0000-0000-0009-000000000059', 'greenhouse', 'Scandit', 'scandit', NULL, 75),
('b0000000-0000-0000-0009-000000000060', 'greenhouse', 'Alloy', 'alloy', NULL, 75),
('b0000000-0000-0000-0009-000000000061', 'greenhouse', 'Culture Amp', 'cultureamp', NULL, 75),
('b0000000-0000-0000-0009-000000000062', 'greenhouse', 'Pie Insurance', 'pieinsurance', NULL, 75),
('b0000000-0000-0000-0009-000000000063', 'greenhouse', 'Typeform', 'typeform', NULL, 75),
('b0000000-0000-0000-0009-000000000064', 'greenhouse', 'Ledgy', 'ledgy', NULL, 75),
('b0000000-0000-0000-0009-000000000065', 'greenhouse', 'Calendly', 'calendly', NULL, 75),
('b0000000-0000-0000-0009-000000000066', 'greenhouse', 'Honeycomb', 'honeycomb', NULL, 75),
('b0000000-0000-0000-0009-000000000067', 'greenhouse', 'Labelbox', 'labelbox', NULL, 75),
('b0000000-0000-0000-0009-000000000068', 'greenhouse', 'CircleCI', 'circleci', NULL, 75),
('b0000000-0000-0000-0009-000000000069', 'greenhouse', 'PlanetScale', 'planetscale', NULL, 75),
('b0000000-0000-0000-0009-000000000070', 'greenhouse', 'Storyblok', 'storyblok', NULL, 75),
('b0000000-0000-0000-0009-000000000071', 'greenhouse', 'Lattice', 'lattice', NULL, 75),
('b0000000-0000-0000-0009-000000000072', 'greenhouse', 'Lithic', 'lithic', NULL, 75),
('b0000000-0000-0000-0009-000000000073', 'greenhouse', 'Branch', 'branch', NULL, 75),
('b0000000-0000-0000-0009-000000000074', 'greenhouse', 'Wallapop', 'wallapop', NULL, 75),
('b0000000-0000-0000-0009-000000000075', 'greenhouse', 'Buildkite', 'buildkite', NULL, 75),
('b0000000-0000-0000-0009-000000000076', 'greenhouse', 'Consensys', 'consensys', NULL, 75),
('b0000000-0000-0000-0009-000000000077', 'greenhouse', 'Descript', 'descript', NULL, 75),
('b0000000-0000-0000-0009-000000000078', 'greenhouse', 'Klaxoon', 'klaxoon', NULL, 75),
('b0000000-0000-0000-0009-000000000079', 'greenhouse', 'Cortex', 'cortex', NULL, 75),
('b0000000-0000-0000-0009-000000000080', 'greenhouse', 'AssemblyAI', 'assemblyai', NULL, 75),
('b0000000-0000-0000-0009-000000000081', 'greenhouse', 'Cleo', 'cleo', NULL, 75),
('b0000000-0000-0000-0009-000000000082', 'greenhouse', 'Form3', 'form3', NULL, 75),
('b0000000-0000-0000-0009-000000000083', 'greenhouse', 'Imbue', 'imbue', NULL, 75),
('b0000000-0000-0000-0009-000000000084', 'greenhouse', 'Remote.com', 'remote', NULL, 75),
('b0000000-0000-0000-0009-000000000085', 'greenhouse', 'Current', 'current', NULL, 75),
('b0000000-0000-0000-0009-000000000086', 'greenhouse', 'Okta', 'okta', NULL, 90),
('b0000000-0000-0000-0009-000000000087', 'greenhouse', 'Zscaler', 'zscaler', NULL, 90),
('b0000000-0000-0000-0009-000000000088', 'greenhouse', 'CoreWeave', 'coreweave', NULL, 90),
('b0000000-0000-0000-0009-000000000089', 'greenhouse', 'Sezzle', 'sezzle', NULL, 90),
('b0000000-0000-0000-0009-000000000090', 'greenhouse', 'Natera', 'natera', NULL, 90),
('b0000000-0000-0000-0009-000000000091', 'greenhouse', 'Klaviyo', 'klaviyo', NULL, 90),
('b0000000-0000-0000-0009-000000000092', 'greenhouse', 'Riot Games', 'riotgames', NULL, 90),
('b0000000-0000-0000-0009-000000000093', 'greenhouse', 'Ripple', 'ripple', NULL, 90),
('b0000000-0000-0000-0009-000000000094', 'greenhouse', 'Scopely', 'scopely', NULL, 85),
('b0000000-0000-0000-0009-000000000095', 'greenhouse', 'Glean', 'gleanwork', NULL, 85),
('b0000000-0000-0000-0009-000000000096', 'greenhouse', 'Epic Games', 'epicgames', NULL, 85),
('b0000000-0000-0000-0009-000000000097', 'greenhouse', 'Postman', 'postman', NULL, 85),
('b0000000-0000-0000-0009-000000000098', 'greenhouse', 'Netskope', 'netskope', NULL, 85),
('b0000000-0000-0000-0009-000000000099', 'greenhouse', 'Cato Networks', 'catonetworks', NULL, 85),
('b0000000-0000-0000-0009-000000000100', 'greenhouse', 'JetBrains', 'jetbrains', NULL, 85),
('b0000000-0000-0000-0009-000000000101', 'greenhouse', 'Rubrik', 'rubrik', NULL, 85),
('b0000000-0000-0000-0009-000000000102', 'greenhouse', 'KnowBe4', 'knowbe4', NULL, 85),
('b0000000-0000-0000-0009-000000000103', 'greenhouse', 'Justworks', 'justworks', NULL, 85),
('b0000000-0000-0000-0009-000000000104', 'greenhouse', 'Gong', 'gongio', NULL, 85),
('b0000000-0000-0000-0009-000000000105', 'greenhouse', 'Commvault', 'commvault', NULL, 85),
('b0000000-0000-0000-0009-000000000106', 'greenhouse', 'Misfits Market', 'misfitsmarket', NULL, 85),
('b0000000-0000-0000-0009-000000000107', 'greenhouse', 'Rockstar Games', 'rockstargames', NULL, 85),
('b0000000-0000-0000-0009-000000000108', 'greenhouse', 'Sigma Computing', 'sigmacomputing', NULL, 85),
('b0000000-0000-0000-0009-000000000109', 'greenhouse', 'Chainguard', 'chainguard', NULL, 85),
('b0000000-0000-0000-0009-000000000110', 'greenhouse', 'Keeper Security', 'keepersecurity', NULL, 85),
('b0000000-0000-0000-0009-000000000111', 'greenhouse', 'Abnormal Security', 'abnormalsecurity', NULL, 85),
('b0000000-0000-0000-0009-000000000112', 'greenhouse', 'Clover Health', 'cloverhealth', NULL, 80),
('b0000000-0000-0000-0009-000000000113', 'greenhouse', 'Tanium', 'tanium', NULL, 80),
('b0000000-0000-0000-0009-000000000114', 'greenhouse', 'FREENOW', 'freenow', NULL, 80),
('b0000000-0000-0000-0009-000000000115', 'greenhouse', 'BeyondTrust', 'beyondtrust', NULL, 80),
('b0000000-0000-0000-0009-000000000116', 'greenhouse', 'Komodo Health', 'komodohealth', NULL, 80),
('b0000000-0000-0000-0009-000000000117', 'greenhouse', 'Tailscale', 'tailscale', NULL, 80),
('b0000000-0000-0000-0009-000000000118', 'greenhouse', 'Arize AI', 'arizeai', NULL, 80),
('b0000000-0000-0000-0009-000000000119', 'greenhouse', 'StockX', 'stockx', NULL, 80),
('b0000000-0000-0000-0009-000000000120', 'greenhouse', 'Ping Identity', 'pingidentity', NULL, 80),
('b0000000-0000-0000-0009-000000000121', 'greenhouse', 'Blockchain.com', 'blockchain', NULL, 80),
('b0000000-0000-0000-0009-000000000122', 'greenhouse', 'Fireworks AI', 'fireworksai', NULL, 80),
('b0000000-0000-0000-0009-000000000123', 'greenhouse', 'Take-Two', 'taketwo', NULL, 80),
('b0000000-0000-0000-0009-000000000124', 'greenhouse', 'project44', 'project44', NULL, 80),
('b0000000-0000-0000-0009-000000000125', 'greenhouse', 'Huntress', 'huntress', NULL, 80),
('b0000000-0000-0000-0009-000000000126', 'greenhouse', 'Lightning AI', 'lightningai', NULL, 80),
('b0000000-0000-0000-0009-000000000127', 'greenhouse', 'Dashlane', 'dashlane', NULL, 75),
('b0000000-0000-0000-0009-000000000128', 'greenhouse', 'Druva', 'druva', NULL, 75),
('b0000000-0000-0000-0009-000000000129', 'greenhouse', 'AfterShip', 'aftership', NULL, 75),
('b0000000-0000-0000-0009-000000000130', 'greenhouse', 'Yotpo', 'yotpo', NULL, 75),
('b0000000-0000-0000-0009-000000000131', 'greenhouse', 'Endor Labs', 'endorlabs', NULL, 75),
('b0000000-0000-0000-0009-000000000132', 'greenhouse', 'Squarespace', 'squarespace', NULL, 75),
('b0000000-0000-0000-0009-000000000133', 'greenhouse', 'Thrive Market', 'thrivemarket', NULL, 75),
('b0000000-0000-0000-0009-000000000134', 'greenhouse', 'Honor', 'honor', NULL, 75),
('b0000000-0000-0000-0009-000000000135', 'greenhouse', 'Narvar', 'narvar', NULL, 75),
('b0000000-0000-0000-0009-000000000136', 'greenhouse', 'Weee!', 'weee', NULL, 75),
('b0000000-0000-0000-0009-000000000137', 'greenhouse', 'Doximity', 'doximity', NULL, 75),
('b0000000-0000-0000-0009-000000000138', 'greenhouse', 'Speechmatics', 'speechmatics', NULL, 75),
('b0000000-0000-0000-0009-000000000139', 'greenhouse', 'Bitwarden', 'bitwarden', NULL, 75),
('b0000000-0000-0000-0009-000000000140', 'greenhouse', 'Galileo', 'galileo', NULL, 75),
('b0000000-0000-0000-0009-000000000141', 'greenhouse', 'Imply', 'imply', NULL, 75),
('b0000000-0000-0000-0009-000000000142', 'greenhouse', 'Collective Health', 'collectivehealth', NULL, 75),
('b0000000-0000-0000-0009-000000000143', 'greenhouse', 'Relay Therapeutics', 'relaytherapeutics', NULL, 75),
('b0000000-0000-0000-0009-000000000144', 'greenhouse', 'FourKites', 'fourkites', NULL, 75),
('b0000000-0000-0000-0009-000000000145', 'greenhouse', 'Dremio', 'dremio', NULL, 75),
('b0000000-0000-0000-0009-000000000146', 'greenhouse', 'Papa', 'papa', NULL, 75),
('b0000000-0000-0000-0009-000000000147', 'greenhouse', 'Contentstack', 'contentstack', NULL, 75),
('b0000000-0000-0000-0009-000000000148', 'greenhouse', 'Cerebral', 'cerebral', NULL, 75),
('b0000000-0000-0000-0009-000000000149', 'greenhouse', 'Public.com', 'public', NULL, 75),
('b0000000-0000-0000-0009-000000000150', 'greenhouse', 'Mercari', 'mercari', NULL, 75),
('b0000000-0000-0000-0009-000000000151', 'greenhouse', 'Bungie', 'bungie', NULL, 75),
('b0000000-0000-0000-0009-000000000152', 'greenhouse', 'Calm', 'calm', NULL, 75),

-- ─────────────────────────────────────────
-- LEVER  (story #269, 5 rows)
-- verified: api.lever.co/v0/postings/{token}?mode=json
-- ─────────────────────────────────────────

('b0000000-0000-0000-0010-000000000001', 'lever', 'Match Group', 'matchgroup', NULL, 85),
('b0000000-0000-0000-0010-000000000002', 'lever', 'PayJoy', 'payjoy', NULL, 85),
('b0000000-0000-0000-0010-000000000003', 'lever', 'Nium', 'nium', NULL, 80),
('b0000000-0000-0000-0010-000000000004', 'lever', 'JumpCloud', 'jumpcloud', NULL, 75),
('b0000000-0000-0000-0010-000000000005', 'lever', 'Sysdig', 'sysdig', NULL, 75),

-- ─────────────────────────────────────────
-- WORKDAY  (story #269, 11 rows, scraper_config with raw CXS url, no token)
-- verified: POST https://{tenant}.wd{n}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs
-- ─────────────────────────────────────────

('b0000000-0000-0000-0011-000000000001', 'workday', 'Richemont', NULL, '{"url": "https://richemont.wd3.myworkdayjobs.com/wday/cxs/richemont/RICHEMONT/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000002', 'workday', 'Visa', NULL, '{"url": "https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000003', 'workday', 'Gsk', NULL, '{"url": "https://gsk.wd5.myworkdayjobs.com/wday/cxs/gsk/GSKCareers/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000004', 'workday', 'Pfizer', NULL, '{"url": "https://pfizer.wd1.myworkdayjobs.com/wday/cxs/pfizer/PfizerCareers/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000005', 'workday', 'Iberdrola', NULL, '{"url": "https://iberdrola.wd3.myworkdayjobs.com/wday/cxs/iberdrola/Iberdrola/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000006', 'workday', 'Juliusbaer', NULL, '{"url": "https://juliusbaer.wd3.myworkdayjobs.com/wday/cxs/juliusbaer/External/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000007', 'workday', 'Paypal', NULL, '{"url": "https://paypal.wd1.myworkdayjobs.com/wday/cxs/paypal/jobs/jobs"}'::jsonb, 90),
('b0000000-0000-0000-0011-000000000008', 'workday', 'Zendesk', NULL, '{"url": "https://zendesk.wd1.myworkdayjobs.com/wday/cxs/zendesk/Zendesk/jobs"}'::jsonb, 85),
('b0000000-0000-0000-0011-000000000009', 'workday', 'Ciena', NULL, '{"url": "https://ciena.wd5.myworkdayjobs.com/wday/cxs/ciena/Careers/jobs"}'::jsonb, 85),
('b0000000-0000-0000-0011-000000000010', 'workday', 'Shell', NULL, '{"url": "https://shell.wd3.myworkdayjobs.com/wday/cxs/shell/ShellCareers/jobs"}'::jsonb, 85),
('b0000000-0000-0000-0011-000000000011', 'workday', 'Repsol', NULL, '{"url": "https://repsol.wd3.myworkdayjobs.com/wday/cxs/repsol/Repsol/jobs"}'::jsonb, 85),

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
-- SMARTRECRUITERS  (story #270, 4 rows, scraper_config with company + countries, no token)
-- verified: GET api.smartrecruiters.com/v1/companies/{company}/postings
-- ─────────────────────────────────────────

INSERT INTO crawler.pull_target (id, source_type, company_name, token, scraper_config, pull_priority) VALUES
('b0000000-0000-0000-0012-000000000001', 'smartrecruiters', 'Bosch Group', NULL,
 '{"company": "BoschGroup", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 85),
('b0000000-0000-0000-0012-000000000002', 'smartrecruiters', 'SGS', NULL,
 '{"company": "SGS", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 80),
('b0000000-0000-0000-0012-000000000003', 'smartrecruiters', 'Delivery Hero', NULL,
 '{"company": "DeliveryHero", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 80),
('b0000000-0000-0000-0012-000000000004', 'smartrecruiters', 'KPN', NULL,
 '{"company": "KPN", "countries": ["nl"], "query": "engineer"}'::jsonb, 80);


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