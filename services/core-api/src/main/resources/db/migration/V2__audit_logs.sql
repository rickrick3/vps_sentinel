-- Journal d'audit en ajout seul (spec §9.5). Aucune clé étrangère : le journal reste indépendant des tables métier.
CREATE TABLE audit_logs (
    id             uuid         PRIMARY KEY DEFAULT uuidv7(),
    occurred_at    timestamptz  NOT NULL DEFAULT now(),
    actor_type     varchar(16)  NOT NULL CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'ANONYMOUS')),
    actor_user_id  uuid,
    actor_email    varchar(254),
    action         varchar(64)  NOT NULL,
    target_type    varchar(32),
    target_id      uuid,
    details        jsonb        NOT NULL DEFAULT '{}',
    ip             inet,
    correlation_id varchar(64)
);

CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id);
CREATE INDEX ix_audit_logs_action ON audit_logs (action);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);

-- L'API ne peut qu'insérer et lire. Les privilèges par défaut (V1) n'accordent jamais TRUNCATE, REFERENCES ni TRIGGER.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM sentinel_app;

-- Les triggers protègent aussi contre une erreur commise avec le rôle propriétaire. Limite assumée : ce rôle peut les
-- désactiver, et un administrateur PostgreSQL peut tout modifier (spec §6.5).
CREATE FUNCTION audit_logs_reject_change() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION 'audit_logs is append-only'; END $$;

CREATE TRIGGER audit_logs_no_update_delete BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();
CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_change();
