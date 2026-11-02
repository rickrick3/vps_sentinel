-- Exécutée par sentinel_owner (conteneur migrate). sentinel_app n'a aucun droit sur le schéma (spec §6.5).

-- Toute table ou séquence créée ensuite par sentinel_owner dans ce schéma est accessible à l'API en lecture et en écriture.
-- Les tables protégées (matrice des permissions, audit) retirent ces droits dans leur propre migration.
ALTER DEFAULT PRIVILEGES IN SCHEMA sentinel GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sentinel GRANT USAGE, SELECT ON SEQUENCES TO sentinel_app;

-- Créée par Flyway avant cette migration, elle ne reçoit pas les privilèges par défaut : lecture seule pour l'API,
-- qui valide les migrations au démarrage.
GRANT SELECT ON flyway_schema_history TO sentinel_app;
