# VPS Sentinel

Plateforme interne de supervision d'infrastructure : inventaire des VPS et des projets, détection des incidents et notifications.

> **La phase 1 n'est pas autorisée en production.** Les conditions préalables (sauvegardes et restauration testées, TLS, secrets de production, watchdog externe…) sont listées au §15 de la [spécification de la phase 1](docs/superpowers/specs/2026-11-02-phase1-foundation-design.md).

## Documentation

- [Architecture technique (TAD v1.0)](docs/architecture/TAD-v1.0.md)
- [Spécification de la phase 1](docs/superpowers/specs/2026-11-02-phase1-foundation-design.md)
- [Plan d'implémentation de la phase 1](docs/superpowers/plans/2026-11-02-phase1-foundation.md)

## Prérequis

- Git ; sous Windows, les scripts s'exécutent dans Git Bash.
- Docker Engine 29 ou plus, avec Docker Compose v2.
- JDK 25 (backend) et Node.js 24 (frontend).

### Sélection du JDK 25

Le JDK 25 n'est pas forcément le JDK par défaut du poste. On le sélectionne pour la session, sans modifier la configuration globale :

```bash
# Git Bash
export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium/jdk-25"* | head -1)"
```

```powershell
# PowerShell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-25*" | Select-Object -First 1).FullName
```

Le wrapper Maven (`./mvnw`, ou `.\mvnw.cmd` sous PowerShell) utilise `JAVA_HOME`.

## Environnement de développement

État actuel : PostgreSQL seul (rôles séparés, base et schéma `sentinel`). Les autres services arrivent avec la phase 1.

```bash
cp .env.example .env                 # une fois ; valeurs factices de développement
bash scripts/gen-dev-secrets.sh      # une fois ; clés dans infrastructure/secrets/dev/ (ignoré par git)

docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres   # démarrage
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps                      # état
docker compose -f docker-compose.yml -f docker-compose.dev.yml down                    # arrêt, données conservées
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v                 # remise à zéro (base vierge)
```

- PostgreSQL est publié sur `127.0.0.1:5432` uniquement, jamais sur le réseau local.
- `infrastructure/postgres/init/01-roles.sh` ne s'exécute que sur un volume vide. Après une modification de ce script ou des mots de passe de `.env`, repartir d'une base vierge avec `down -v`.
- Rôles PostgreSQL : `postgres` (initialisation uniquement), `sentinel_owner` (migrations), `sentinel_app` (API, sans aucun droit de création).

## Règles git

Ce dépôt se trouve dans le dossier d'un autre dépôt (`expertiz`), dont il est indépendant.

- Toute commande git s'exécute depuis la racine de `vps-sentinel/`.
- **Ne jamais exécuter `git add .` ni `git add -A` depuis `expertiz`.**
- Aucun secret n'est versionné : `.env`, clés (`*.pem`, `*.key`) et `infrastructure/secrets/` sont ignorés.

## Conventions

- Package Java racine : `com.expertiz.sentinel.core`.
- Identifiants de code en anglais, interface utilisateur en français.
- Commits au format [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`…).
- Fins de ligne LF (sauf `*.cmd` et `*.bat`), UTF-8, indentation définie par `.editorconfig`.
- Scripts en Bash : `set -euo pipefail`, chemins toujours entre guillemets.
- Hygiène du dépôt : `bash scripts/check-repo.sh` (fins de ligne, bit exécutable, fichiers sensibles). Il lit l'index git : indexer un nouveau fichier avant de le vérifier.
