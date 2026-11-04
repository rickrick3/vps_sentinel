-- Projets et environnements (spec §9.4). Supprimés logiquement ; les noms se libèrent à la suppression.

CREATE TABLE projects (
    id             uuid          PRIMARY KEY DEFAULT uuidv7(),
    name           varchar(100)  NOT NULL,
    description    varchar(2000),
    -- http(s) uniquement, comme en entrée (spec §10.3) : pas de javascript: ni de data: stockable.
    repository_url varchar(500)  CONSTRAINT ck_projects_repository_url CHECK (repository_url ~* '^https?://'),
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    deleted_at     timestamptz,
    version        bigint        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_projects_name_active ON projects (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE environments (
    id         uuid         PRIMARY KEY DEFAULT uuidv7(),
    project_id uuid         NOT NULL CONSTRAINT fk_environments_project REFERENCES projects (id),
    name       varchar(50)  NOT NULL CONSTRAINT ck_environments_name CHECK (name ~ '^[A-Za-z0-9][A-Za-z0-9._-]*$'),
    kind       varchar(16)  NOT NULL CONSTRAINT ck_environments_kind CHECK (kind IN ('PRODUCTION', 'STAGING', 'DEVELOPMENT', 'OTHER')),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    version    bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_environments_project_name_active ON environments (project_id, lower(name)) WHERE deleted_at IS NULL;
CREATE INDEX ix_environments_project ON environments (project_id);
