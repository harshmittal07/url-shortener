#!/bin/sh
# Creates the migration and application database users before Flyway runs (R21, plan §5).
# Runs once, from the postgres image's /docker-entrypoint-initdb.d, as the superuser.
# Names and passwords are passed as psql variables, so no SQL is built from shell strings.
set -eu

: "${DB_MIGRATION_USER:?DB_MIGRATION_USER is required}"
: "${DB_MIGRATION_PASSWORD:?DB_MIGRATION_PASSWORD is required}"
: "${DB_APP_USER:?DB_APP_USER is required}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD is required}"

psql --no-psqlrc -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v db="$POSTGRES_DB" \
    -v migration_user="$DB_MIGRATION_USER" -v migration_password="$DB_MIGRATION_PASSWORD" \
    -v app_user="$DB_APP_USER" -v app_password="$DB_APP_PASSWORD" <<'SQL'
CREATE ROLE :"migration_user" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, CREATE ON DATABASE :"db" TO :"migration_user";
GRANT CONNECT ON DATABASE :"db" TO :"app_user";
SQL
