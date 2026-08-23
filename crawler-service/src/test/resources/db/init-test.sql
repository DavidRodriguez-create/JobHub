CREATE SCHEMA IF NOT EXISTS crawler;

-- Story #52 (ADR 0005): FTS trigger function (mirrors db/init/017-job-post-perf.sql
-- and job-service/src/test/resources/db/init-test.sql). crawler-service maps
-- search_vector as a read-only field on JobPostEntity for schema parity with prod
-- (Hibernate `validate`); the function is defined here so it exists if a future
-- crawler-service test attaches the trigger. No trigger is attached by default —
-- crawler-service writes job_post rows via Hibernate and does not query
-- search_vector.
CREATE OR REPLACE FUNCTION crawler.trg_job_post_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END;
$$;
