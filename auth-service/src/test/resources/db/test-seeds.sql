-- test-seeds.sql — no user rows; component tests register through the API
ALTER TABLE auth.user ALTER COLUMN first_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN last_name TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN email TYPE TEXT;
ALTER TABLE auth.user ALTER COLUMN password_hash TYPE TEXT;
