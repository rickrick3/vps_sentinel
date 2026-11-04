#!/usr/bin/env bash
# Régénère contracts/openapi/core-api.yaml depuis l'API (spec §13.6) : OpenApiContractIT démarre l'application sur une
# base de test et, avec UPDATE_OPENAPI=true, réécrit le contrat au lieu de le comparer. Relire le diff avant de commiter.
# Prérequis : Docker (Testcontainers) et un JDK 25 (JAVA_HOME).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../services/core-api"
UPDATE_OPENAPI=true ./mvnw -B -q test -Dtest=OpenApiContractIT
echo "Contrat régénéré : contracts/openapi/core-api.yaml"
