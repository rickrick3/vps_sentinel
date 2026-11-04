-- Identité (spec §9.2) : utilisateurs, rôles, permissions et matrice fixe du spec §8.12.

CREATE TABLE users (
    id                   uuid         PRIMARY KEY DEFAULT uuidv7(),
    -- Stocké normalisé (spec §10.3) : la contrainte garantit que deux casses d'un même email ne coexistent pas.
    email                varchar(254) NOT NULL CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),
    full_name            varchar(120) NOT NULL,
    -- Argon2id uniquement (spec §8.6) : aucun mot de passe en clair ni hash d'un autre format ne peut être stocké.
    password_hash        varchar(255) NOT NULL CONSTRAINT ck_users_password_hash CHECK (password_hash LIKE '{argon2}$argon2id$%'),
    password_expires_at  timestamptz,
    status               varchar(16)  NOT NULL CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    must_change_password boolean      NOT NULL DEFAULT true,
    last_login_at        timestamptz,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    deleted_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_users_email_active ON users (email) WHERE deleted_at IS NULL;

CREATE TABLE roles (
    id    uuid        PRIMARY KEY DEFAULT uuidv7(),
    code  varchar(32) NOT NULL CONSTRAINT uq_roles_code UNIQUE,
    label varchar(64) NOT NULL
);

CREATE TABLE permissions (
    id          uuid         PRIMARY KEY DEFAULT uuidv7(),
    code        varchar(64)  NOT NULL CONSTRAINT uq_permissions_code UNIQUE,
    description varchar(255) NOT NULL
);

CREATE TABLE role_permissions (
    role_id       uuid NOT NULL CONSTRAINT fk_role_permissions_role REFERENCES roles (id),
    permission_id uuid NOT NULL CONSTRAINT fk_role_permissions_permission REFERENCES permissions (id),
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id)
);

-- Un seul rôle par utilisateur en phase 1.
CREATE TABLE user_roles (
    user_id uuid NOT NULL CONSTRAINT fk_user_roles_user REFERENCES users (id),
    role_id uuid NOT NULL CONSTRAINT fk_user_roles_role REFERENCES roles (id),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT uq_user_roles_user UNIQUE (user_id)
);

INSERT INTO roles (code, label) VALUES
    ('SUPER_ADMIN', 'Super-administrateur'),
    ('INFRA_ADMIN', 'Administrateur infrastructure'),
    ('DEVOPS', 'DevOps'),
    ('DEVELOPER', 'Développeur'),
    ('VIEWER', 'Lecteur');

INSERT INTO permissions (code, description) VALUES
    ('server:read', 'Consulter les VPS'),
    ('server:write', 'Créer et modifier les VPS, statut administratif compris'),
    ('server:delete', 'Supprimer les VPS'),
    ('project:read', 'Consulter les projets, environnements, domaines et déploiements'),
    ('project:write', 'Créer et modifier les projets et environnements, ajouter et retirer domaines et déploiements'),
    ('project:delete', 'Supprimer les projets et environnements'),
    ('user:read', 'Consulter les utilisateurs'),
    ('user:manage', 'Créer, modifier, désactiver et supprimer les utilisateurs, réinitialiser leur mot de passe'),
    ('audit:read', 'Consulter le journal d''audit');

-- Matrice du spec §8.12 (27 lignes), vérifiée ligne à ligne par PermissionMatrixIT.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES
        ('SUPER_ADMIN', 'server:read'), ('SUPER_ADMIN', 'server:write'), ('SUPER_ADMIN', 'server:delete'),
        ('SUPER_ADMIN', 'project:read'), ('SUPER_ADMIN', 'project:write'), ('SUPER_ADMIN', 'project:delete'),
        ('SUPER_ADMIN', 'user:read'), ('SUPER_ADMIN', 'user:manage'), ('SUPER_ADMIN', 'audit:read'),
        ('INFRA_ADMIN', 'server:read'), ('INFRA_ADMIN', 'server:write'), ('INFRA_ADMIN', 'server:delete'),
        ('INFRA_ADMIN', 'project:read'), ('INFRA_ADMIN', 'project:write'), ('INFRA_ADMIN', 'project:delete'),
        ('INFRA_ADMIN', 'user:read'), ('INFRA_ADMIN', 'audit:read'),
        ('DEVOPS', 'server:read'), ('DEVOPS', 'server:write'),
        ('DEVOPS', 'project:read'), ('DEVOPS', 'project:write'), ('DEVOPS', 'project:delete'),
        ('DEVELOPER', 'server:read'), ('DEVELOPER', 'project:read'), ('DEVELOPER', 'project:write'),
        ('VIEWER', 'server:read'), ('VIEWER', 'project:read')
       ) AS matrix (role_code, permission_code)
  JOIN roles r ON r.code = matrix.role_code
  JOIN permissions p ON p.code = matrix.permission_code;

-- Matrice fixe (spec §6.5) : l'API ne peut que la lire. Les privilèges par défaut (V1) n'accordent jamais TRUNCATE.
REVOKE INSERT, UPDATE, DELETE ON roles, permissions, role_permissions FROM sentinel_app;
