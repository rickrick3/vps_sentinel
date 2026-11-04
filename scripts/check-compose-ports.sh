#!/usr/bin/env bash
# Ports publiés par les combinaisons Compose (spec §6.3, §13.9), réutilisé par la CI :
#   dev  : le gateway et PostgreSQL, sur la boucle locale uniquement ;
#   prod : le gateway seul, sur 80 et 443.
# Aucun autre service ne publie de port. Adresse d'écoute, port publié et port du conteneur sont comparés.
set -euo pipefail
# Une erreur de Compose dans $(…) arrête le script avec son propre message.
shopt -s inherit_errexit

cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"

# Seule la forme de la configuration est examinée : valeurs factices, et le .env du poste n'est pas lu.
empty_env="$(mktemp)"
trap 'rm -f "$empty_env"' EXIT
export POSTGRES_PASSWORD=factice SENTINEL_DB_OWNER_PASSWORD=factice SENTINEL_DB_APP_PASSWORD=factice
export SENTINEL_TLS_CERT_DIR=./factice/certs SENTINEL_ACME_WEBROOT=./factice/acme SENTINEL_SECRETS_DIR=./factice/secrets

# {"service": ["adresse:publié->conteneur", …]}, services et ports triés.
published_ports() {
  local config
  config="$(docker compose --env-file "$empty_env" -f docker-compose.yml -f "docker-compose.$1.yml" config --format json)"
  node -e '
      let input = "";
      process.stdin.on("data", (chunk) => (input += chunk)).on("end", () => {
        const ports = {};
        for (const [name, service] of Object.entries(JSON.parse(input).services ?? {})) {
          for (const port of service.ports ?? []) {
            const host = port.host_ip ? `${port.host_ip}:` : "";
            (ports[name] ??= []).push(`${host}${port.published}->${port.target}`);
          }
        }
        const sorted = Object.keys(ports).sort().map((name) => [name, ports[name].sort()]);
        console.log(JSON.stringify(Object.fromEntries(sorted)));
      });' <<<"$config"
}

errors=0
check() {
  local environment="$1" expected="$2" actual
  actual="$(published_ports "$environment")"
  if [[ "$actual" == "$expected" ]]; then
    echo "$environment : $actual"
  else
    echo "ÉCHEC $environment : attendu $expected, obtenu $actual" >&2
    errors=$((errors + 1))
  fi
}

check dev '{"gateway":["127.0.0.1:8088->80"],"postgres":["127.0.0.1:5432->5432"]}'
check prod '{"gateway":["443->443","80->80"]}'

if ((errors > 0)); then
  exit 1
fi
echo "OK"
