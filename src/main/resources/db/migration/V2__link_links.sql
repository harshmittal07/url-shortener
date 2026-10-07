CREATE TABLE link.links (
    id            uuid          PRIMARY KEY,
    code          varchar(7)    NOT NULL,
    target_url    varchar(2048) NOT NULL,
    owner_key_id  uuid          NOT NULL,   -- key ID from identity; no cross-schema FK (R5)
    status        varchar(16)   NOT NULL,
    created_at    timestamptz   NOT NULL,
    CONSTRAINT links_code_uk     UNIQUE (code),
    CONSTRAINT links_code_ck     CHECK (code ~ '^[0-9A-Za-z]{7}$'),
    CONSTRAINT links_status_ck   CHECK (status IN ('ACTIVE', 'DELETED'))
);
GRANT USAGE ON SCHEMA link TO ${appUser};
GRANT SELECT, INSERT ON link.links TO ${appUser};
GRANT UPDATE (status) ON link.links TO ${appUser};
