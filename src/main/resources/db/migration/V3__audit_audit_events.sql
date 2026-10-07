CREATE TABLE audit.audit_events (
    id              uuid        PRIMARY KEY,
    occurred_at     timestamptz NOT NULL,
    request_id      varchar(64) NOT NULL,
    actor_key_id    uuid,
    action          varchar(32) NOT NULL,
    resource_type   varchar(32),
    resource_id     varchar(64),
    outcome         varchar(16) NOT NULL,
    reason_code     varchar(64),
    client_ip_hash  char(64)
);
GRANT USAGE ON SCHEMA audit TO ${appUser};
GRANT SELECT, INSERT ON audit.audit_events TO ${appUser};   -- append-only (S-11)
