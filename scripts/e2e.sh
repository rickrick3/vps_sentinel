#!/usr/bin/env bash
# Tests E2E (spec §13.8) : pile dev complète et vierge, puis Playwright ; la pile E2E est supprimée en sortie.
# Elle tourne sous le projet Compose « vps-sentinel-e2e » (tests/e2e/docker-compose.e2e.yml) : conteneurs et volumes
# distincts, si bien que la pile et la base de développement ne sont jamais touchées. Le port 8088 doit être libre.
# Prérequis : Docker, Node 24, .env (cp .env.example .env).
set -euo pipefail

cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
# Sous Git Bash, un chemin de conteneur passé à Docker serait converti en chemin Windows.
export MSYS_NO_PATHCONV=1

if [[ ! -f .env ]]; then
  echo "ÉCHEC : .env absent (cp .env.example .env)" >&2
  exit 1
fi
if [[ ! -f infrastructure/secrets/dev/jwt-private.pem || ! -f infrastructure/secrets/dev/hmac.key ]]; then
  bash scripts/gen-dev-secrets.sh
fi

compose() {
  docker compose -p vps-sentinel-e2e -f docker-compose.yml -f docker-compose.dev.yml -f tests/e2e/docker-compose.e2e.yml "$@"
}

# Base vierge au départ, pile et volume supprimés à la fin, que les tests réussissent ou non.
compose down -v --remove-orphans
trap 'compose down -v --remove-orphans' EXIT
compose up -d --build --wait

# Identifiants de l'administrateur initial, lus par les tests.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

# Sous-shell : le trap de sortie s'exécute depuis la racine du dépôt, où se trouvent les fichiers Compose.
(
  cd tests/e2e
  [[ -d node_modules ]] || npm ci --no-audit --no-fund
  npx playwright install chromium
  npx playwright test
)
