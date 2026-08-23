#!/bin/bash
# init-users.sh
# Create database users with credentials from environment variables
# Called by the PostgreSQL container during initialization

set -e

# Get passwords from env vars (defaults to local dev values if not set)
ADMIN_PASSWORD="${JOBHUB_ADMIN_PASSWORD:-jobhub_admin_password}"
CRAWLER_PASSWORD="${CRAWLER_PASSWORD:-crawler_password}"
JOB_PASSWORD="${JOB_PASSWORD:-job_password}"
AUTH_PASSWORD="${AUTH_PASSWORD:-auth_password}"
APPLICATIONS_PASSWORD="${APPLICATIONS_PASSWORD:-applications_password}"
NOTIFICATION_PASSWORD="${NOTIFICATION_PASSWORD:-notification_password}"

echo "Creating database users..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Admin user (schema migrations, maintenance)
    CREATE USER jobhub_admin WITH PASSWORD '$ADMIN_PASSWORD';
    GRANT CONNECT ON DATABASE jobhub TO jobhub_admin;
    ALTER USER jobhub_admin CREATEDB;

    -- Service users (read-write on their schema only)
    CREATE USER crawler_user WITH PASSWORD '$CRAWLER_PASSWORD';
    CREATE USER job_user WITH PASSWORD '$JOB_PASSWORD';
    CREATE USER auth_user WITH PASSWORD '$AUTH_PASSWORD';
    CREATE USER applications_user WITH PASSWORD '$APPLICATIONS_PASSWORD';
    CREATE USER notification_user WITH PASSWORD '$NOTIFICATION_PASSWORD';

    -- Grant basic database access to all users
    GRANT CONNECT ON DATABASE jobhub TO crawler_user;
    GRANT CONNECT ON DATABASE jobhub TO job_user;
    GRANT CONNECT ON DATABASE jobhub TO auth_user;
    GRANT CONNECT ON DATABASE jobhub TO applications_user;
    GRANT CONNECT ON DATABASE jobhub TO notification_user;
EOSQL

echo "Database users created successfully"
