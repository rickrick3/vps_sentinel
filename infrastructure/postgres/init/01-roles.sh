#!/usr/bin/env bash
# Exécuté par l'image postgres, uniquement sur un volume vide (spec §6.5).
# Crée sentinel_owner (migrations) et sentinel_app (API), la base et le schéma sentinel.
# Le superutilisateur ne sert qu'ici ; les privilèges sur les tables sont posés par les migrations.
set -euo pipefail

: "${SENTINEL_DB_OWNER_PASSWORD:?SENTINEL_DB_OWNER_PASSWORD est requis}"
: "${SENTINEL_DB_APP_PASSWORD:?SENTINEL_DB_APP_PASSWORD est requis}"

# Les mots de passe passent par des variables psql (:'var', citées par psql), jamais par le shell dans le SQL.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_pw="$SENTINEL_DB_OWNER_PASSWORD" \
  -v app_pw="$SENTINEL_DB_APP_PASSWORD" <<'SQL'
CREATE ROLE sentinel_owner LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB PASSWORD :'owner_pw';
CREATE ROLE sentinel_app   LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB PASSWORD :'app_pw';
CREATE DATABASE sentinel OWNER sentinel_owner;
REVOKE CONNECT, TEMPORARY ON DATABASE sentinel FROM PUBLIC;
GRANT CONNECT ON DATABASE sentinel TO sentinel_app;

\connect sentinel
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA sentinel AUTHORIZATION sentinel_owner;
GRANT USAGE ON SCHEMA sentinel TO sentinel_app;
ALTER ROLE sentinel_owner SET search_path = sentinel;
ALTER ROLE sentinel_app   SET search_path = sentinel;
SQL
