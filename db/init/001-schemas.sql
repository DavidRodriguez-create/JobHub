-- 001-schemas.sql
-- Create schemas with least-privilege user access

CREATE SCHEMA crawler;
CREATE SCHEMA job;
CREATE SCHEMA auth;
CREATE SCHEMA applications;

-- Set ownership and grant schema usage to respective service users
-- crawler schema
ALTER SCHEMA crawler OWNER TO crawler_user;
GRANT USAGE ON SCHEMA crawler TO crawler_user;
GRANT CREATE ON SCHEMA crawler TO crawler_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA crawler GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crawler_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA crawler GRANT USAGE, SELECT ON SEQUENCES TO crawler_user;

-- job schema (default for job-service)
ALTER SCHEMA job OWNER TO job_user;
GRANT USAGE ON SCHEMA job TO job_user;
GRANT CREATE ON SCHEMA job TO job_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA job GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO job_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA job GRANT USAGE, SELECT ON SEQUENCES TO job_user;

-- auth schema
ALTER SCHEMA auth OWNER TO auth_user;
GRANT USAGE ON SCHEMA auth TO auth_user;
GRANT CREATE ON SCHEMA auth TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT USAGE, SELECT ON SEQUENCES TO auth_user;

-- applications schema
ALTER SCHEMA applications OWNER TO applications_user;
GRANT USAGE ON SCHEMA applications TO applications_user;
GRANT CREATE ON SCHEMA applications TO applications_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA applications GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO applications_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA applications GRANT USAGE, SELECT ON SEQUENCES TO applications_user;

-- Admin user gets full permissions on all schemas for migrations
GRANT USAGE, CREATE ON SCHEMA crawler TO jobhub_admin;
GRANT USAGE, CREATE ON SCHEMA job TO jobhub_admin;
GRANT USAGE, CREATE ON SCHEMA auth TO jobhub_admin;
GRANT USAGE, CREATE ON SCHEMA applications TO jobhub_admin;