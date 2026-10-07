#!/usr/bin/env bash
# Manual checks against a running `docker compose up --build` (plan §12):
#   AC35, AC34    database users, one-shot Flyway as the migration user, app user only, no DDL
#   AC47, S-15    non-root, minimal image, read-only root filesystem with tmpfs /tmp, round trip
#   AC38, AC45,   ECS logs, no actuator on the public port, Swagger UI off by default
#   AC46
#   AC42 (D15)    database outage: 503 service-unavailable within about 3 s, then recovery
#
# Usage: ADMIN_KEY=<bootstrap admin key> scripts/smoke-test.sh [--skip-db-outage]
#   ADMIN_KEY  the key whose hash is BOOTSTRAP_ADMIN_KEY_HASH (scripts/new-admin-key.sh). Read only
#              from the environment; never printed, and passed to curl on stdin, not as an argument.
#   APP_URL    defaults to http://localhost:8080.
# The outage check stops and restarts the db container. Run from any directory; exits non-zero on
# any failed check.
set -euo pipefail

: "${ADMIN_KEY:?Set ADMIN_KEY to the bootstrap admin key (see scripts/new-admin-key.sh)}"
APP_URL="${APP_URL:-http://localhost:8080}"
run_outage=true
for arg in "$@"; do
    case "$arg" in
        --skip-db-outage) run_outage=false ;;
        *) echo "Unknown argument: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

failures=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1"; failures=$((failures + 1)); }
check() {
    local description=$1
    shift
    if "$@"; then pass "$description"; else fail "$description"; fi
}
section() { printf '\n== %s\n' "$1"; }

# --- helpers -----------------------------------------------------------------------------------

container_of() { docker compose ps -a -q "$1"; }
inspect() { docker inspect --format "$2" "$(container_of "$1")"; }

# psql as the superuser inside the db container; SQL on stdin, names passed as psql variables.
db_query() {
    docker compose exec -T db psql --no-psqlrc -v ON_ERROR_STOP=1 -U postgres -d urlshortener -tA \
        -v migration_user="$MIGRATION_USER" -v app_user="$APP_USER"
}

# Runs one statement as the application user inside a rolled-back transaction; succeeds only if
# the database refuses it with a privilege error.
app_user_refused() {
    local output
    if output=$(docker compose exec -T db sh -c \
        'psql --no-psqlrc -v ON_ERROR_STOP=1 -U "$DB_APP_USER" -d urlshortener -tA 2>&1' <<SQL
BEGIN;
$1;
ROLLBACK;
SQL
    ); then
        return 1
    fi
    printf '%s' "$output" | grep -Eq 'permission denied|must be owner'
}

# request METHOD PATH [KEY] [JSON_BODY]: sets STATUS and TIME; body and headers go to $tmp.
request() {
    local method=$1 path=$2 key=${3:-} body=${4:-}
    local args=(-s -o "$tmp/body" -D "$tmp/headers" -w '%{http_code} %{time_total}' -X "$method" --max-time 15)
    if [ -n "$body" ]; then
        args+=(-H 'Content-Type: application/json' --data "$body")
    fi
    local result
    if [ -n "$key" ]; then
        result=$(printf 'Authorization: Bearer %s\n' "$key" | curl "${args[@]}" -H @- "$APP_URL$path" || true)
    else
        result=$(curl "${args[@]}" "$APP_URL$path" || true)
    fi
    STATUS=${result%% *}
    TIME=${result##* }
}

json_field() { sed -n 's/.*"'"$1"'":"\([^"]*\)".*/\1/p' "$tmp/body"; }
header() { grep -i "^$1:" "$tmp/headers" | head -n 1 | cut -d' ' -f2- | tr -d '\r'; }
equals() { [ "$1" = "$2" ]; }
faster_than() { awk -v t="$1" -v limit="$2" 'BEGIN { exit !(t < limit) }'; }

wait_for_app() {
    local attempt
    for attempt in $(seq 1 60); do
        request GET /v3/api-docs
        if [ "$STATUS" = 200 ]; then return 0; fi
        sleep 1
    done
    return 1
}

# --- checks ------------------------------------------------------------------------------------

section "Startup"
check "app answers on $APP_URL" wait_for_app
MIGRATION_USER=$(docker compose exec -T db printenv DB_MIGRATION_USER)
APP_USER=$(docker compose exec -T db printenv DB_APP_USER)

section "AC35: database users and the one-shot migration (R21)"
check "both database users exist" equals "$(db_query <<'SQL'
SELECT count(*) FROM pg_roles WHERE rolname IN (:'migration_user', :'app_user');
SQL
)" 2
check "migrate ran once and exited 0" equals "$(inspect migrate '{{.State.Status}} {{.State.ExitCode}}')" "exited 0"
migrate_finished=$(inspect migrate '{{.State.FinishedAt}}')
app_started=$(inspect app '{{.State.StartedAt}}')
check "app started after migrate finished" test "$migrate_finished" \< "$app_started"
check "every Flyway migration succeeded and was installed by the migration user" equals "$(db_query <<'SQL'
SELECT count(*) >= 3 AND bool_and(success AND installed_by = :'migration_user')
FROM flyway.flyway_schema_history WHERE version IS NOT NULL;
SQL
)" t
check "the migration user owns the flyway, identity, link and audit schemas" equals "$(db_query <<'SQL'
SELECT count(*) FROM pg_namespace n JOIN pg_roles r ON r.oid = n.nspowner
WHERE n.nspname IN ('flyway', 'identity', 'link', 'audit') AND r.rolname = :'migration_user';
SQL
)" 4
app_env_names=$(inspect app '{{range .Config.Env}}{{println .}}{{end}}' | cut -d= -f1)
check "the app container's environment has no migration credentials" \
    sh -c '! printf "%s\n" "$1" | grep -Eq "^(DB_MIGRATION_|FLYWAY_|POSTGRES_PASSWORD)"' sh "$app_env_names"
check "the app container's environment has the application user" \
    sh -c 'printf "%s\n" "$1" | grep -qx DB_APP_USER' sh "$app_env_names"

section "AC34, AC31: the application user cannot run DDL or change audit rows"
for statement in \
    "CREATE TABLE identity.smoke_ddl (i int)" \
    "CREATE TABLE link.smoke_ddl (i int)" \
    "CREATE TABLE audit.smoke_ddl (i int)" \
    "CREATE TABLE public.smoke_ddl (i int)" \
    "CREATE TABLE flyway.smoke_ddl (i int)" \
    "CREATE SCHEMA smoke_ddl" \
    "ALTER TABLE link.links ADD COLUMN smoke_ddl int" \
    "DROP TABLE identity.api_keys" \
    "UPDATE audit.audit_events SET outcome = outcome" \
    "DELETE FROM audit.audit_events" \
    "SELECT count(*) FROM flyway.flyway_schema_history"; do
    check "refused for the app user: $statement" app_user_refused "$statement"
done

section "AC47, S-15: container hardening (R33)"
check "the app image runs as the nonroot user" equals "$(inspect app '{{.Config.User}}')" nonroot
app_uids=$(docker top "$(container_of app)" -o pid,uid | awk 'NR > 1 { print $2 }')
check "the app process runs as a non-zero uid (got $(echo $app_uids))" \
    sh -c '[ -n "$1" ] && ! printf "%s\n" "$1" | grep -qx 0' sh "$app_uids"
check "the root filesystem is read-only" equals "$(inspect app '{{.HostConfig.ReadonlyRootfs}}')" true
check "/tmp is a tmpfs" sh -c 'printf "%s" "$1" | grep -q "\"/tmp\""' sh "$(inspect app '{{json .HostConfig.Tmpfs}}')"
check "the image has no shell (distroless)" sh -c '! docker compose exec -T app sh -c true > /dev/null 2>&1'
check "the database port is not published" equals "$(inspect db '{{json .NetworkSettings.Ports}}' | grep -c HostPort || true)" 0

section "AC47: create -> redirect -> delete round trip"
target="https://example.com/smoke/$(date +%s)"
request POST /api/keys "$ADMIN_KEY"
check "admin issues an owner key (201)" equals "$STATUS" 201
owner_key=$(json_field key)
check "the response carries X-Request-Id" test -n "$(header X-Request-Id)"
request POST /api/links "$owner_key" "{\"targetUrl\":\"$target\"}"
check "owner creates a link (201)" equals "$STATUS" 201
code=$(json_field code)
request GET "/$code"
check "visitor is redirected (302)" equals "$STATUS" 302
check "Location is the target" equals "$(header Location)" "$target"
request DELETE "/api/links/$code" "$owner_key"
check "owner deletes the link (204)" equals "$STATUS" 204
request GET "/$code"
check "the deleted code answers 404" equals "$STATUS" 404
check "the app's connections run as the application user" equals "$(db_query <<'SQL'
SELECT count(*) > 0 AND bool_and(usename = :'app_user') FROM pg_stat_activity
WHERE datname = 'urlshortener' AND application_name = 'PostgreSQL JDBC Driver';
SQL
)" t

section "AC38, AC45, AC46: logs, actuator and Swagger UI"
check "app log lines are ECS JSON" \
    sh -c '[ "$(docker compose logs --no-log-prefix app | tail -n 5 | grep -vc "^{\"@timestamp\"")" = 0 ]'
request GET /actuator/health
check "the public port serves no actuator (404)" equals "$STATUS" 404
check "the management port is not published" \
    sh -c '! curl -s --max-time 3 -o /dev/null "http://localhost:8081/actuator/health"'
request GET /swagger-ui/index.html
check "Swagger UI is off by default (404)" equals "$STATUS" 404
request GET /v3/api-docs
check "the OpenAPI document is served (200)" equals "$STATUS" 200

if [ "$run_outage" = true ]; then
    section "AC42 (D15): database outage"
    docker compose stop db > /dev/null 2>&1
    request GET "/api/links/$code" "$owner_key"
    check "/api/** answers 503 while the database is down (got $STATUS)" equals "$STATUS" 503
    check "with code service-unavailable" grep -q '"code":"service-unavailable"' "$tmp/body"
    check "within about 3 s (took ${TIME}s)" faster_than "$TIME" 3.5
    request GET /abcdefg
    check "a redirect answers 503 while the database is down (got $STATUS)" equals "$STATUS" 503
    check "within about 3 s (took ${TIME}s)" faster_than "$TIME" 3.5
    docker compose start db > /dev/null 2>&1
    recovered() {
        local attempt
        for attempt in $(seq 1 30); do
            request GET /abcdefg
            if [ "$STATUS" = 404 ]; then return 0; fi
            sleep 1
        done
        return 1
    }
    check "the app recovers once the database is back" recovered
else
    section "AC42 (D15): database outage skipped (--skip-db-outage)"
fi

printf '\n%s\n' "$([ "$failures" = 0 ] && echo "All checks passed." || echo "$failures check(s) failed.")"
[ "$failures" = 0 ]
