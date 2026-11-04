-- Inventaire des VPS (spec §9.3). Les règles du spec §10.3 sont aussi des contraintes : l'API ne peut pas stocker une
-- valeur qu'elle aurait refusée en entrée.

CREATE TABLE servers (
    id           uuid          PRIMARY KEY DEFAULT uuidv7(),
    name         varchar(100)  NOT NULL CONSTRAINT ck_servers_name CHECK (name ~ '^[A-Za-z0-9][A-Za-z0-9 ._-]*$'),
    hostname     varchar(253)  CONSTRAINT ck_servers_hostname_lower CHECK (hostname = lower(hostname)),
    public_ip    inet,
    private_ip   inet,
    provider     varchar(100),
    region       varchar(100),
    os_family    varchar(16)   CONSTRAINT ck_servers_os_family CHECK (os_family IN
                                   ('UBUNTU', 'DEBIAN', 'RHEL', 'ROCKY', 'ALMALINUX', 'CENTOS', 'OTHER')),
    os_version   varchar(32),
    cpu_cores    smallint      CONSTRAINT ck_servers_cpu_cores CHECK (cpu_cores BETWEEN 1 AND 1024),
    ram_mb       int           CONSTRAINT ck_servers_ram_mb CHECK (ram_mb BETWEEN 1 AND 16777216),
    disk_gb      int           CONSTRAINT ck_servers_disk_gb CHECK (disk_gb BETWEEN 1 AND 1048576),
    admin_status varchar(16)   NOT NULL DEFAULT 'ACTIVE'
                               CONSTRAINT ck_servers_admin_status CHECK (admin_status IN ('ACTIVE', 'MAINTENANCE', 'DECOMMISSIONED')),
    description  varchar(2000),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted_at   timestamptz,
    version      bigint        NOT NULL DEFAULT 0
);

-- Unicité insensible à la casse parmi les VPS non supprimés : un nom se libère à la suppression.
CREATE UNIQUE INDEX uq_servers_name_active ON servers (lower(name)) WHERE deleted_at IS NULL;

-- Tags remplacés en bloc par PUT ; un VPS supprimé logiquement les conserve.
CREATE TABLE server_tags (
    server_id uuid        NOT NULL CONSTRAINT fk_server_tags_server REFERENCES servers (id),
    tag       varchar(50) NOT NULL CONSTRAINT ck_server_tags_tag CHECK (tag ~ '^[a-z0-9][a-z0-9._-]*$'),
    CONSTRAINT pk_server_tags PRIMARY KEY (server_id, tag)
);

CREATE INDEX ix_server_tags_tag ON server_tags (tag);
