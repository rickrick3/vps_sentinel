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

État actuel : PostgreSQL, conteneur de migration et Core API. Le gateway et le frontend arrivent avec la suite de la phase 1.

```bash
cp .env.example .env                 # une fois ; valeurs factices de développement
bash scripts/gen-dev-secrets.sh      # une fois ; clés dans infrastructure/secrets/dev/ (ignoré par git)

docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait    # pile complète
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps -a                   # état (migrate : Exited (0))
docker compose -f docker-compose.yml -f docker-compose.dev.yml down                    # arrêt, données conservées
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v                 # remise à zéro (base vierge)
```

API lancée depuis le poste, sur `localhost:8080`, avec la base de la pile Compose :

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm migrate       # attend la fin des migrations
set -a; . ./.env; set +a             # SENTINEL_DB_APP_PASSWORD
cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Sans `/run/secrets`, l'API lancée depuis le poste en profil `dev` génère des clés JWT et HMAC éphémères, avec un avertissement dans les logs : les jetons émis ne survivent pas à un redémarrage. Dans tout autre profil, un secret absent empêche le démarrage.

Tests du backend (Docker requis : Testcontainers démarre un PostgreSQL 18 réel) : `cd services/core-api && ./mvnw verify`.

- `migrate` applique les migrations avec `sentinel_owner`, puis s'arrête. `core-api` ne démarre que si `migrate` a réussi, et refuse de démarrer si une migration manque ou a été modifiée : l'API valide les migrations sans jamais les exécuter.
- PostgreSQL est publié sur `127.0.0.1:5432` uniquement, jamais sur le réseau local. `core-api` n'est pas publié : il sera exposé par le gateway.
- `infrastructure/postgres/init/01-roles.sh` ne s'exécute que sur un volume vide. Après une modification de ce script ou des mots de passe de `.env`, repartir d'une base vierge avec `down -v`.
- Rôles PostgreSQL : `postgres` (initialisation uniquement), `sentinel_owner` (migrations), `sentinel_app` (API, sans aucun droit de création).

## Sécurité de l'API

Détails et justifications : §8 de la [spécification](docs/superpowers/specs/2026-11-02-phase1-foundation-design.md).

- **Authentification :** uniquement `Authorization: Bearer <access token>`, jamais dans l'URL ni par cookie. L'access token est un JWT RS256 de 15 minutes, signé avec `jwt-private.pem`. Sont vérifiés : la signature, l'en-tête `typ: at+jwt`, `iss` (`vps-sentinel`), `aud` (`core-api`), `exp`, `iat`, et la présence et le type de chaque claim. Aucune tolérance de décalage d'horloge.
- **Refus par défaut :** sans jeton, seules ces routes répondent : `GET /actuator/health`, le contrat OpenAPI (dev et test) et `POST /api/v1/auth/{login,refresh,logout}`. Ces trois dernières exigent un en-tête `Origin` présent dans `SENTINEL_ALLOWED_ORIGINS` (format `http(s)://hôte[:port]`, sans chemin ni joker ; une entrée invalide empêche le démarrage). Chaque contrôleur déclare un `@PreAuthorize` ; les permissions viennent du claim `perms`.
- **Changement de mot de passe imposé :** un jeton `pcr` n'ouvre que `POST /api/v1/auth/change-password`, `POST /api/v1/auth/logout` et `GET /api/v1/me` (méthode et chemin exacts). Tout le reste répond 403 `password-change-required`.
- **Erreurs :** les 401 et 403, comme les requêtes refusées par le pare-feu de Spring Security, sont rendus en Problem Details. La raison d'un refus de jeton n'est jamais renvoyée.
- **Politique des mots de passe :** de 12 à 128 caractères, comptés en points de code Unicode. Différent de l'email, sans tenir compte de la casse ni des espaces. Aucune règle de composition (NIST SP 800-63B).
- **Hachage :** Argon2id au minimum recommandé par l'OWASP (19 Mio, 2 itérations, parallélisme 1). La base refuse tout autre format de hash.
- **Mots de passe temporaires :** 16 caractères tirés par `SecureRandom` d'un alphabet de 56 symboles sans caractères ambigus, soit environ 93 bits d'entropie.
- **Matrice des permissions :** fixe, insérée par migration. `sentinel_app` ne peut que la lire : toute écriture est refusée par PostgreSQL.

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
