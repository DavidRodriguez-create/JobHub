
-- 015-crawler-smartrecruiters.sql
--
-- Story #270: add SmartRecruitersJobSourceClient support and seed the four
-- target companies named in the story (Bosch Group, SGS, Delivery Hero, KPN).
--
-- Purely additive: no source_type schema/enum/CHECK change (crawler.pull_target.source_type
-- is a plain VARCHAR(32), so "smartrecruiters" is just a new value, same as every other
-- source_type string).
--
-- Forward-only migration, hand-applied to existing volumes per the runbook (see CLAUDE.md ->
-- 'Running the full stack in Podman'). Safe to re-run:
--   - SmartRecruiters rows have token = NULL (config lives entirely in scraper_config), so
--     ON CONFLICT on (source_type, token) cannot dedup them (NULL <> NULL in SQL). Each row is
--     instead guarded by its own INSERT ... SELECT ... WHERE NOT EXISTS, keyed on
--     scraper_config->>'company' (the natural key for a SmartRecruiters target), mirroring the
--     Workday rows in db/init/019-crawler-sources-expand.sql.
--
-- Fresh volumes get the same 4 rows via db/seeds/011-crawler-seeds.sql (appended there, with
-- explicit ids, no ON CONFLICT needed since the seed only ever runs once against an empty schema).

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'smartrecruiters', 'Bosch Group', NULL,
       '{"company": "BoschGroup", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 85
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'smartrecruiters'
      AND scraper_config->>'company' = 'BoschGroup'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'smartrecruiters', 'SGS', NULL,
       '{"company": "SGS", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 80
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'smartrecruiters'
      AND scraper_config->>'company' = 'SGS'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'smartrecruiters', 'Delivery Hero', NULL,
       '{"company": "DeliveryHero", "countries": ["fr", "es", "ch", "us", "nl"]}'::jsonb, 80
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'smartrecruiters'
      AND scraper_config->>'company' = 'DeliveryHero'
);

INSERT INTO crawler.pull_target (source_type, company_name, token, scraper_config, pull_priority)
SELECT 'smartrecruiters', 'KPN', NULL,
       '{"company": "KPN", "countries": ["nl"], "query": "engineer"}'::jsonb, 80
WHERE NOT EXISTS (
    SELECT 1 FROM crawler.pull_target
    WHERE source_type = 'smartrecruiters'
      AND scraper_config->>'company' = 'KPN'
);
