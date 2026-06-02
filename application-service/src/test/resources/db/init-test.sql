CREATE SCHEMA IF NOT EXISTS applications;

DO $$ BEGIN
    CREATE TYPE applications.status AS ENUM (
        'applied','screening','interviewing','offered','rejected','accepted','withdrawn','ghosted'
    );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
