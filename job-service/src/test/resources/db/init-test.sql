-- Runs before Hibernate on the DevServices Postgres container.
-- Hibernate's drop-and-create (default-schema=crawler) owns table creation for every
-- @Entity in this module, including TriggerRequestEntity → crawler.trigger_request
-- (Story #7 / ADR 0003, mirrors db/init/016-crawler-trigger-request.sql). This script
-- only needs to ensure the schema itself exists before Hibernate runs.
CREATE SCHEMA IF NOT EXISTS crawler;

-- Story #52 (ADR 0005): FTS trigger function (mirrors db/init/017-job-post-perf.sql).
-- Defined at the schema level so it survives Hibernate's drop-and-create of
-- crawler.job_post — the function itself is not dropped when the table is recreated.
-- test-seeds.sql attaches this function to crawler.job_post via a trigger after
-- Hibernate has created the table.
CREATE OR REPLACE FUNCTION crawler.trg_job_post_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$;
