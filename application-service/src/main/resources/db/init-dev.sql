CREATE SCHEMA IF NOT EXISTS applications;

-- Type required by 030-applications.sql; in prod it lives in db/init/030-applications.sql.
DO $$ BEGIN
    CREATE TYPE applications.status AS ENUM (
        'applied','screening','interviewing','offered','rejected','accepted','withdrawn','ghosted'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
