CREATE TABLE identity.api_keys (
    id          uuid        PRIMARY KEY,
    key_prefix  varchar(12) NOT NULL,
    key_hash    char(64)    NOT NULL,
    created_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    CONSTRAINT api_keys_key_prefix_uk UNIQUE (key_prefix)
);
GRANT USAGE ON SCHEMA identity TO ${appUser};
GRANT SELECT, INSERT ON identity.api_keys TO ${appUser};
