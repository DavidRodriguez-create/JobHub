
-- 019-crawler-sources-expand.sql
--
-- Story #269: expand crawler.pull_target with 168 net-new verified sources
-- (152 Greenhouse + 5 Lever, token-based; 11 Workday, CXS-url-based).
-- Sources were probed live against the exact endpoints our clients use
-- (db/tools/verify_sources.py) before being added here; no speculative tokens.
--
-- Forward-only migration, hand-applied to existing volumes per the runbook
-- (see CLAUDE.md -> 'Running the full stack in Podman'). Safe to re-run:
--   - Greenhouse/Lever rows are deduped by the existing
--     uq_pull_target_source_token (source_type, token) constraint via
--     ON CONFLICT ... DO NOTHING.
--   - Workday rows have token = NULL, so ON CONFLICT on (source_type, token)
--     cannot dedup them (NULL <> NULL in SQL). Each Workday row is instead
--     guarded by its own INSERT ... SELECT ... WHERE NOT EXISTS, keyed on
--     scraper_config->>'url' (the natural key for a Workday CXS endpoint).
--
-- Fresh volumes get the same 168 rows via db/seeds/011-crawler-seeds.sql
-- (appended there, with explicit ids, no ON CONFLICT needed since the seed
-- only ever runs once against an empty schema).

-- ─────────────────────────────────────────
-- GREENHOUSE + LEVER (157 rows, token-based)
-- verified: boards-api.greenhouse.io/v1/boards/{token}/jobs
--           api.lever.co/v0/postings/{token}?mode=json
-- ─────────────────────────────────────────

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority) VALUES
  ('greenhouse', 'MongoDB', 'mongodb', NULL, 90),
  ('greenhouse', 'HelloFresh', 'hellofresh', NULL, 90),
  ('greenhouse', 'On (On Running)', 'onrunning', NULL, 90),
  ('greenhouse', 'Verkada', 'verkada', NULL, 90),
  ('greenhouse', 'Toast', 'toast', NULL, 90),
  ('greenhouse', 'Roblox', 'roblox', NULL, 90),
  ('greenhouse', 'Celonis', 'celonis', NULL, 90),
  ('greenhouse', 'Adyen', 'adyen', NULL, 90),
  ('greenhouse', 'Block (Square)', 'block', NULL, 90),
  ('greenhouse', 'Pinterest', 'pinterest', NULL, 90),
  ('greenhouse', 'ClickHouse', 'clickhouse', NULL, 90),
  ('greenhouse', 'Tide', 'tide', NULL, 85),
  ('greenhouse', 'Fivetran', 'fivetran', NULL, 85),
  ('greenhouse', 'Grafana Labs', 'grafanalabs', NULL, 85),
  ('greenhouse', 'Smartsheet', 'smartsheet', NULL, 85),
  ('greenhouse', 'SoFi', 'sofi', NULL, 85),
  ('greenhouse', 'Faire', 'faire', NULL, 85),
  ('greenhouse', 'Dialpad', 'dialpad', NULL, 85),
  ('greenhouse', 'Bloomreach', 'bloomreach', NULL, 85),
  ('greenhouse', 'N26', 'n26', NULL, 85),
  ('greenhouse', 'Vercel', 'vercel', NULL, 85),
  ('greenhouse', 'Chime', 'chime', NULL, 85),
  ('greenhouse', 'Proton', 'proton', NULL, 85),
  ('greenhouse', 'Fireblocks', 'fireblocks', NULL, 80),
  ('greenhouse', 'Cabify', 'cabify', NULL, 80),
  ('greenhouse', 'Neo4j', 'neo4j', NULL, 80),
  ('greenhouse', 'New Relic', 'newrelic', NULL, 80),
  ('greenhouse', 'Mercury', 'mercury', NULL, 80),
  ('greenhouse', 'Temporal', 'temporaltechnologies', NULL, 80),
  ('greenhouse', 'Catawiki', 'catawiki', NULL, 80),
  ('greenhouse', 'Checkr', 'checkr', NULL, 80),
  ('greenhouse', 'Bitpanda', 'bitpanda', NULL, 80),
  ('greenhouse', 'Carta', 'carta', NULL, 80),
  ('greenhouse', 'Amplitude', 'amplitude', NULL, 80),
  ('greenhouse', 'SingleStore', 'singlestore', NULL, 80),
  ('greenhouse', 'Snorkel AI', 'snorkelai', NULL, 80),
  ('greenhouse', 'Bird (MessageBird)', 'bird', NULL, 80),
  ('greenhouse', 'Collibra', 'collibra', NULL, 80),
  ('greenhouse', 'Dataiku', 'dataiku', NULL, 80),
  ('greenhouse', 'Raisin', 'raisin', NULL, 80),
  ('greenhouse', 'Airtable', 'airtable', NULL, 80),
  ('greenhouse', 'Showpad', 'showpad', NULL, 80),
  ('greenhouse', 'Mixpanel', 'mixpanel', NULL, 80),
  ('greenhouse', 'CockroachDB', 'cockroachlabs', NULL, 80),
  ('greenhouse', 'LaunchDarkly', 'launchdarkly', NULL, 80),
  ('greenhouse', 'Betterment', 'betterment', NULL, 80),
  ('greenhouse', 'Marqeta', 'marqeta', NULL, 80),
  ('greenhouse', 'Coalition', 'coalition', NULL, 80),
  ('greenhouse', 'Salesloft', 'salesloft', NULL, 80),
  ('greenhouse', 'Shift Technology', 'shifttechnology', NULL, 80),
  ('greenhouse', 'VTEX', 'vtex', NULL, 80),
  ('greenhouse', 'Sumo Logic', 'sumologic', NULL, 75),
  ('greenhouse', 'PagerDuty', 'pagerduty', NULL, 75),
  ('greenhouse', 'YugabyteDB', 'yugabyte', NULL, 75),
  ('greenhouse', 'Gemini', 'gemini', NULL, 75),
  ('greenhouse', 'commercetools', 'commercetools', NULL, 75),
  ('greenhouse', 'Webflow', 'webflow', NULL, 75),
  ('greenhouse', 'Make (Integromat)', 'make', NULL, 75),
  ('greenhouse', 'Scandit', 'scandit', NULL, 75),
  ('greenhouse', 'Alloy', 'alloy', NULL, 75),
  ('greenhouse', 'Culture Amp', 'cultureamp', NULL, 75),
  ('greenhouse', 'Pie Insurance', 'pieinsurance', NULL, 75),
  ('greenhouse', 'Typeform', 'typeform', NULL, 75),
  ('greenhouse', 'Ledgy', 'ledgy', NULL, 75),
  ('greenhouse', 'Calendly', 'calendly', NULL, 75),
  ('greenhouse', 'Honeycomb', 'honeycomb', NULL, 75),
  ('greenhouse', 'Labelbox', 'labelbox', NULL, 75),
  ('greenhouse', 'CircleCI', 'circleci', NULL, 75),
  ('greenhouse', 'PlanetScale', 'planetscale', NULL, 75),
  ('greenhouse', 'Storyblok', 'storyblok', NULL, 75),
  ('greenhouse', 'Lattice', 'lattice', NULL, 75),
  ('greenhouse', 'Lithic', 'lithic', NULL, 75),
  ('greenhouse', 'Branch', 'branch', NULL, 75),
  ('greenhouse', 'Wallapop', 'wallapop', NULL, 75),
  ('greenhouse', 'Buildkite', 'buildkite', NULL, 75),
  ('greenhouse', 'Consensys', 'consensys', NULL, 75),
  ('greenhouse', 'Descript', 'descript', NULL, 75),
  ('greenhouse', 'Klaxoon', 'klaxoon', NULL, 75),
  ('greenhouse', 'Cortex', 'cortex', NULL, 75),
  ('greenhouse', 'AssemblyAI', 'assemblyai', NULL, 75),
  ('greenhouse', 'Cleo', 'cleo', NULL, 75),
  ('greenhouse', 'Form3', 'form3', NULL, 75),
  ('greenhouse', 'Imbue', 'imbue', NULL, 75),
  ('greenhouse', 'Remote.com', 'remote', NULL, 75),
  ('greenhouse', 'Current', 'current', NULL, 75),
  ('lever', 'Match Group', 'matchgroup', NULL, 85),
  ('lever', 'PayJoy', 'payjoy', NULL, 85),
  ('lever', 'Nium', 'nium', NULL, 80),
  ('lever', 'JumpCloud', 'jumpcloud', NULL, 75),
  ('lever', 'Sysdig', 'sysdig', NULL, 75),
  ('greenhouse', 'Okta', 'okta', NULL, 90),
  ('greenhouse', 'Zscaler', 'zscaler', NULL, 90),
  ('greenhouse', 'CoreWeave', 'coreweave', NULL, 90),
  ('greenhouse', 'Sezzle', 'sezzle', NULL, 90),
  ('greenhouse', 'Natera', 'natera', NULL, 90),
  ('greenhouse', 'Klaviyo', 'klaviyo', NULL, 90),
  ('greenhouse', 'Riot Games', 'riotgames', NULL, 90),
  ('greenhouse', 'Ripple', 'ripple', NULL, 90),
  ('greenhouse', 'Scopely', 'scopely', NULL, 85),
  ('greenhouse', 'Glean', 'gleanwork', NULL, 85),
  ('greenhouse', 'Epic Games', 'epicgames', NULL, 85),
  ('greenhouse', 'Postman', 'postman', NULL, 85),
  ('greenhouse', 'Netskope', 'netskope', NULL, 85),
  ('greenhouse', 'Cato Networks', 'catonetworks', NULL, 85),
  ('greenhouse', 'JetBrains', 'jetbrains', NULL, 85),
  ('greenhouse', 'Rubrik', 'rubrik', NULL, 85),
  ('greenhouse', 'KnowBe4', 'knowbe4', NULL, 85),
  ('greenhouse', 'Justworks', 'justworks', NULL, 85),
  ('greenhouse', 'Gong', 'gongio', NULL, 85),
  ('greenhouse', 'Commvault', 'commvault', NULL, 85),
  ('greenhouse', 'Misfits Market', 'misfitsmarket', NULL, 85),
  ('greenhouse', 'Rockstar Games', 'rockstargames', NULL, 85),
  ('greenhouse', 'Sigma Computing', 'sigmacomputing', NULL, 85),
  ('greenhouse', 'Chainguard', 'chainguard', NULL, 85),
  ('greenhouse', 'Keeper Security', 'keepersecurity', NULL, 85),
  ('greenhouse', 'Abnormal Security', 'abnormalsecurity', NULL, 85),
  ('greenhouse', 'Clover Health', 'cloverhealth', NULL, 80),
  ('greenhouse', 'Tanium', 'tanium', NULL, 80),
  ('greenhouse', 'FREENOW', 'freenow', NULL, 80),
  ('greenhouse', 'BeyondTrust', 'beyondtrust', NULL, 80),
  ('greenhouse', 'Komodo Health', 'komodohealth', NULL, 80),
  ('greenhouse', 'Tailscale', 'tailscale', NULL, 80),
  ('greenhouse', 'Arize AI', 'arizeai', NULL, 80),
  ('greenhouse', 'StockX', 'stockx', NULL, 80),
  ('greenhouse', 'Ping Identity', 'pingidentity', NULL, 80),
  ('greenhouse', 'Blockchain.com', 'blockchain', NULL, 80),
  ('greenhouse', 'Fireworks AI', 'fireworksai', NULL, 80),
  ('greenhouse', 'Take-Two', 'taketwo', NULL, 80),
  ('greenhouse', 'project44', 'project44', NULL, 80),
  ('greenhouse', 'Huntress', 'huntress', NULL, 80),
  ('greenhouse', 'Lightning AI', 'lightningai', NULL, 80),
  ('greenhouse', 'Dashlane', 'dashlane', NULL, 75),
  ('greenhouse', 'Druva', 'druva', NULL, 75),
  ('greenhouse', 'AfterShip', 'aftership', NULL, 75),
  ('greenhouse', 'Yotpo', 'yotpo', NULL, 75),
  ('greenhouse', 'Endor Labs', 'endorlabs', NULL, 75),
  ('greenhouse', 'Squarespace', 'squarespace', NULL, 75),
  ('greenhouse', 'Thrive Market', 'thrivemarket', NULL, 75),
  ('greenhouse', 'Honor', 'honor', NULL, 75),
  ('greenhouse', 'Narvar', 'narvar', NULL, 75),
  ('greenhouse', 'Weee!', 'weee', NULL, 75),
  ('greenhouse', 'Doximity', 'doximity', NULL, 75),
  ('greenhouse', 'Speechmatics', 'speechmatics', NULL, 75),
  ('greenhouse', 'Bitwarden', 'bitwarden', NULL, 75),
  ('greenhouse', 'Galileo', 'galileo', NULL, 75),
  ('greenhouse', 'Imply', 'imply', NULL, 75),
  ('greenhouse', 'Collective Health', 'collectivehealth', NULL, 75),
  ('greenhouse', 'Relay Therapeutics', 'relaytherapeutics', NULL, 75),
  ('greenhouse', 'FourKites', 'fourkites', NULL, 75),
  ('greenhouse', 'Dremio', 'dremio', NULL, 75),
  ('greenhouse', 'Papa', 'papa', NULL, 75),
  ('greenhouse', 'Contentstack', 'contentstack', NULL, 75),
  ('greenhouse', 'Cerebral', 'cerebral', NULL, 75),
  ('greenhouse', 'Public.com', 'public', NULL, 75),
  ('greenhouse', 'Mercari', 'mercari', NULL, 75),
  ('greenhouse', 'Bungie', 'bungie', NULL, 75),
  ('greenhouse', 'Calm', 'calm', NULL, 75)
ON CONFLICT (source_type, token) DO NOTHING;

-- ─────────────────────────────────────────
-- WORKDAY (11 rows, scraper_config-based, token NULL)
-- verified: POST https://{tenant}.wd{n}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs
-- Idempotent via NOT EXISTS on scraper_config->>'url' (see header note above).
-- ─────────────────────────────────────────

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Richemont', NULL,
       '{"url": "https://richemont.wd3.myworkdayjobs.com/wday/cxs/richemont/RICHEMONT/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://richemont.wd3.myworkdayjobs.com/wday/cxs/richemont/RICHEMONT/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Visa', NULL,
       '{"url": "https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://visa.wd5.myworkdayjobs.com/wday/cxs/visa/Visa/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Gsk', NULL,
       '{"url": "https://gsk.wd5.myworkdayjobs.com/wday/cxs/gsk/GSKCareers/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://gsk.wd5.myworkdayjobs.com/wday/cxs/gsk/GSKCareers/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Pfizer', NULL,
       '{"url": "https://pfizer.wd1.myworkdayjobs.com/wday/cxs/pfizer/PfizerCareers/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://pfizer.wd1.myworkdayjobs.com/wday/cxs/pfizer/PfizerCareers/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Iberdrola', NULL,
       '{"url": "https://iberdrola.wd3.myworkdayjobs.com/wday/cxs/iberdrola/Iberdrola/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://iberdrola.wd3.myworkdayjobs.com/wday/cxs/iberdrola/Iberdrola/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Juliusbaer', NULL,
       '{"url": "https://juliusbaer.wd3.myworkdayjobs.com/wday/cxs/juliusbaer/External/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://juliusbaer.wd3.myworkdayjobs.com/wday/cxs/juliusbaer/External/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Paypal', NULL,
       '{"url": "https://paypal.wd1.myworkdayjobs.com/wday/cxs/paypal/jobs/jobs"}'::jsonb, 90
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://paypal.wd1.myworkdayjobs.com/wday/cxs/paypal/jobs/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Zendesk', NULL,
       '{"url": "https://zendesk.wd1.myworkdayjobs.com/wday/cxs/zendesk/Zendesk/jobs"}'::jsonb, 85
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://zendesk.wd1.myworkdayjobs.com/wday/cxs/zendesk/Zendesk/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Ciena', NULL,
       '{"url": "https://ciena.wd5.myworkdayjobs.com/wday/cxs/ciena/Careers/jobs"}'::jsonb, 85
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://ciena.wd5.myworkdayjobs.com/wday/cxs/ciena/Careers/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Shell', NULL,
       '{"url": "https://shell.wd3.myworkdayjobs.com/wday/cxs/shell/ShellCareers/jobs"}'::jsonb, 85
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://shell.wd3.myworkdayjobs.com/wday/cxs/shell/ShellCareers/jobs'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'workday', 'Repsol', NULL,
       '{"url": "https://repsol.wd3.myworkdayjobs.com/wday/cxs/repsol/Repsol/jobs"}'::jsonb, 85
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'workday'
      AND scraper_config->>'url' = 'https://repsol.wd3.myworkdayjobs.com/wday/cxs/repsol/Repsol/jobs'
);
