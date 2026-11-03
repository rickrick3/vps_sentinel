-- Sessions et limitation des tentatives (spec §8.2, §8.4 et §9.2). Les colonnes de hash et d'empreinte n'acceptent
-- que leur format : un jeton, un identifiant ou une IP en clair ne peuvent pas y être stockés.

CREATE TABLE refresh_tokens (
    id                 uuid        PRIMARY KEY DEFAULT uuidv7(),
    user_id            uuid        NOT NULL CONSTRAINT fk_refresh_tokens_user REFERENCES users (id),
    family_id          uuid        NOT NULL,
    -- SHA-256 du jeton, en hexadécimal : seul le hash est stocké.
    token_hash         char(64)    NOT NULL CONSTRAINT uq_refresh_tokens_token_hash UNIQUE
                                   CONSTRAINT ck_refresh_tokens_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    session_started_at timestamptz NOT NULL,
    issued_at          timestamptz NOT NULL,
    expires_at         timestamptz NOT NULL,
    revoked_at         timestamptz,
    revoked_reason     varchar(32) CONSTRAINT ck_refresh_tokens_revoked_reason CHECK (revoked_reason IN
                           ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'USER_DISABLED', 'ROLE_CHANGED', 'PASSWORD_CHANGED',
                            'PASSWORD_RESET')),
    replaced_by        uuid,
    user_agent         varchar(255),
    ip                 inet,
    CONSTRAINT ck_refresh_tokens_revocation CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE login_throttles (
    id               uuid        PRIMARY KEY DEFAULT uuidv7(),
    scope            varchar(16) NOT NULL,
    -- Empreintes HMAC tronquées à 128 bits, en hexadécimal (spec §8.4).
    account_fp       char(32)    CONSTRAINT ck_login_throttles_account_fp CHECK (account_fp ~ '^[0-9a-f]{32}$'),
    ip_fp            char(32)    NOT NULL CONSTRAINT ck_login_throttles_ip_fp CHECK (ip_fp ~ '^[0-9a-f]{32}$'),
    failure_count    int         NOT NULL CONSTRAINT ck_login_throttles_failure_count CHECK (failure_count >= 0),
    first_failure_at timestamptz NOT NULL,
    last_failure_at  timestamptz NOT NULL,
    blocked_until    timestamptz,
    CONSTRAINT ck_login_throttles_scope CHECK (
        (scope = 'IP' AND account_fp IS NULL) OR (scope = 'ACCOUNT_IP' AND account_fp IS NOT NULL))
);

CREATE UNIQUE INDEX uq_login_throttles_key ON login_throttles (scope, coalesce(account_fp, ''), ip_fp);
CREATE INDEX ix_login_throttles_account ON login_throttles (account_fp);
