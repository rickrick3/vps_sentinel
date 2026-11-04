-- Domaines et déploiements d'un environnement (spec §9.4). Supprimés physiquement avec leur environnement, après un
-- audit avec instantané (spec §9.6).

CREATE TABLE domains (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    environment_id uuid         NOT NULL CONSTRAINT fk_domains_environment REFERENCES environments (id),
    -- Stocké normalisé (spec §10.3) : minuscules, au moins un point.
    hostname       varchar(253) NOT NULL CONSTRAINT ck_domains_hostname CHECK (hostname = lower(hostname) AND position('.' IN hostname) > 0),
    is_primary     boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_domains_hostname ON domains (hostname);
-- Au plus un domaine principal par environnement.
CREATE UNIQUE INDEX uq_domains_primary ON domains (environment_id) WHERE is_primary;
CREATE INDEX ix_domains_environment ON domains (environment_id);

-- Lien environnement ↔ VPS. Un déploiement ne vise jamais un VPS supprimé ou DECOMMISSIONED : vérifié sous verrou
-- (spec §7.2), la clé étrangère seule ne voit pas la suppression logique.
CREATE TABLE deployments (
    id             uuid        PRIMARY KEY DEFAULT uuidv7(),
    environment_id uuid        NOT NULL CONSTRAINT fk_deployments_environment REFERENCES environments (id),
    server_id      uuid        NOT NULL CONSTRAINT fk_deployments_server REFERENCES servers (id),
    role           varchar(50) CONSTRAINT ck_deployments_role CHECK (role ~ '^[A-Za-z0-9][A-Za-z0-9 ._-]*$'),
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    CONSTRAINT uq_deployments_env_server UNIQUE (environment_id, server_id)
);

CREATE INDEX ix_deployments_server ON deployments (server_id);
