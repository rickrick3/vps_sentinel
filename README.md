# VPS Sentinel

Plateforme interne de supervision d'infrastructure : inventaire des VPS et des projets, détection des incidents et notifications.

> **La phase 1 n'est pas autorisée en production.** Les conditions préalables (sauvegardes et restauration testées, TLS, secrets de production, watchdog externe…) sont listées au §15 de la [spécification de la phase 1](docs/superpowers/specs/2026-11-02-phase1-foundation-design.md) et suivies dans [les conditions préalables à la mise en production](docs/deployment/production-prerequisites.md).

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

La pile complète (PostgreSQL, migrations, Core API, frontend et gateway) sert l'application sur <http://localhost:8088>. Pour développer, l'API et le frontend se lancent aussi depuis le poste (voir plus bas).

```bash
cp .env.example .env                 # une fois ; valeurs factices de développement
bash scripts/gen-dev-secrets.sh      # une fois ; clés dans infrastructure/secrets/dev/ (ignoré par git)

docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build --wait    # pile complète : http://localhost:8088
docker compose -f docker-compose.yml -f docker-compose.dev.yml ps -a                   # état (migrate : Exited (0))
docker compose -f docker-compose.yml -f docker-compose.dev.yml down                    # arrêt, données conservées
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v                 # remise à zéro (base vierge)
```

API lancée depuis le poste, sur `localhost:8080`, avec la base de la pile Compose :

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres
docker compose -f docker-compose.yml -f docker-compose.dev.yml run --rm --build migrate   # image reconstruite : elle embarque les migrations
set -a; . ./.env; set +a             # mot de passe de sentinel_app, origines autorisées, admin initial
cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Sans `/run/secrets`, l'API lancée depuis le poste en profil `dev` génère des clés JWT et HMAC éphémères, avec un avertissement dans les logs : les jetons émis ne survivent pas à un redémarrage. Dans tout autre profil, un secret absent empêche le démarrage.

Tests du backend (Docker requis : Testcontainers démarre un PostgreSQL 18 réel) : `cd services/core-api && ./mvnw verify`.

Frontend, avec l'API lancée depuis le poste comme ci-dessus :

```bash
cd frontend
npm ci                               # une fois, puis à chaque changement de package-lock.json
npm run dev                          # http://localhost:5173 ; /api passe par le proxy de Vite vers 127.0.0.1:8080
npm run lint && npm run typecheck && npm test && npm run build    # vérifications complètes
npm run gen:api                      # après une modification du contrat OpenAPI ; commiter src/api/schema.d.ts
```

- Le port 5173 est fixe : `http://localhost:5173` est l'origine autorisée en dev (`SENTINEL_ALLOWED_ORIGINS`). S'il est occupé, Vite refuse de démarrer plutôt que de changer de port.
- La session reste en mémoire dans l'onglet : un rechargement la restaure par le cookie de refresh, et une déconnexion se propage aux autres onglets.

- `migrate` applique les migrations avec `sentinel_owner`, puis s'arrête. `core-api` ne démarre que si `migrate` a réussi, et refuse de démarrer si une migration manque ou a été modifiée : l'API valide les migrations sans jamais les exécuter.
- Seuls le gateway (`127.0.0.1:8088`) et PostgreSQL (`127.0.0.1:5432`) sont publiés, sur la boucle locale uniquement, jamais sur le réseau local. `core-api` et `frontend` ne sont joignables que par le gateway.
- `infrastructure/postgres/init/01-roles.sh` ne s'exécute que sur un volume vide. Après une modification de ce script ou des mots de passe de `.env`, repartir d'une base vierge avec `down -v`.
- Rôles PostgreSQL : `postgres` (initialisation uniquement), `sentinel_owner` (migrations), `sentinel_app` (API, sans aucun droit de création).

### Premier accès

Au premier démarrage, core-api crée le SUPER_ADMIN décrit par `SENTINEL_BOOTSTRAP_ADMIN_*` (`.env`). Son mot de passe doit être changé à la première connexion : d'ici là, son jeton n'ouvre que `GET /api/v1/me`, le changement de mot de passe et le logout. **Retirez ensuite ces variables de `.env`.**

Par l'interface : <http://localhost:8088> (pile complète) ou <http://localhost:5173> (frontend du poste), connexion avec ces identifiants, puis changement de mot de passe imposé. Par l'API, avec l'API lancée depuis le poste (voir plus haut) :

```bash
API=http://127.0.0.1:8080/api/v1
ORIGIN=http://localhost:5173         # une origine de SENTINEL_ALLOWED_ORIGINS, exigée par login, refresh et logout

# Connexion : accessToken (15 min) dans le corps, refresh token dans le cookie sentinel_rt (Set-Cookie)
curl -si -X POST "$API/auth/login" -H "Origin: $ORIGIN" -H 'Content-Type: application/json' \
     -d '{"email":"admin@sentinel.local","password":"<SENTINEL_BOOTSTRAP_ADMIN_PASSWORD>"}'

# Changement de mot de passe imposé : renvoie une nouvelle paire de jetons
curl -s -X POST "$API/auth/change-password" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"currentPassword":"<actuel>","newPassword":"<nouveau, 12 caractères minimum>"}'

curl -s "$API/me" -H "Authorization: Bearer <accessToken>"

# Le cookie est Secure : curl ne le renvoie pas seul en HTTP, il se passe à la main
curl -s -X POST "$API/auth/refresh" -H "Origin: $ORIGIN" -H "Cookie: sentinel_rt=<valeur>"
curl -s -X POST "$API/auth/logout"  -H "Origin: $ORIGIN" -H "Cookie: sentinel_rt=<valeur>"
```

Ensuite, un SUPER_ADMIN gère les comptes (`user:read` suffit pour lire) :

```bash
# Création : le mot de passe temporaire (72 h) n'est renvoyé qu'une fois, dans cette réponse
curl -s -X POST "$API/users" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"email":"alice@exemple.fr","fullName":"Alice Martin","role":"DEVOPS"}'
curl -s "$API/users?q=alice&role=DEVOPS&sort=fullName,asc" -H "Authorization: Bearer <accessToken>"
# Modification partielle : champs absents inchangés, version obligatoire (lue dans la réponse précédente)
curl -s -X PATCH "$API/users/<id>" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"role":"VIEWER","version":0}'
curl -s -X POST "$API/users/<id>/reset-password" -H "Authorization: Bearer <accessToken>"
```

Inventaire : VPS, projets, environnements, domaines et déploiements (permissions du rôle, voir la matrice du spec §8.12) :

```bash
curl -s -X POST "$API/servers" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"name":"VPS-01","hostname":"vps-01.exemple.fr","publicIp":"203.0.113.10","provider":"OVH","tags":["prod","web"]}'
curl -s -X POST "$API/projects" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"name":"Demo","repositoryUrl":"https://github.com/exemple/demo"}'
curl -s -X POST "$API/projects/<projectId>/environments" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"name":"prod","kind":"PRODUCTION"}'
curl -s -X POST "$API/environments/<environmentId>/domains" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"hostname":"demo.exemple.fr","isPrimary":true}'
curl -s -X POST "$API/environments/<environmentId>/deployments" -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' \
     -d '{"serverId":"<serverId>","role":"web"}'
curl -s "$API/servers/<serverId>/deployments" -H "Authorization: Bearer <accessToken>"
```

`PUT` remplace la ressource entière (champ facultatif absent = vidé) et exige la `version` lue. Un VPS utilisé par un déploiement ne peut être ni supprimé ni passé en `DECOMMISSIONED` (409 `server-in-use`).

Journal d'audit (`audit:read` : SUPER_ADMIN et INFRA_ADMIN), du plus récent au plus ancien ; `from` inclus, `to` exclu :

```bash
curl -s "$API/audit-logs?targetType=SERVER&from=2026-11-01T00:00:00Z&to=2026-11-02T00:00:00Z&size=50" \
     -H "Authorization: Bearer <accessToken>"
```

Contrat OpenAPI : `contracts/openapi/core-api.yaml`, d'où le frontend génère ses types. Le build échoue s'il ne correspond plus à l'API ; après une modification d'endpoint, le régénérer avec `bash scripts/export-openapi.sh` (Docker requis), puis relire le diff.

Avec la pile complète, les mêmes requêtes passent par le gateway : `API=http://localhost:8088/api/v1` et `ORIGIN=http://localhost:8088`.

## Gateway

Configuration : `infrastructure/gateway/` (spec §6.4). `nginx.dev.conf` sert HTTP sur `127.0.0.1:8088` ; `nginx.prod.conf` termine TLS sur 443, et son port 80 ne sert que le défi ACME et la redirection vers HTTPS.

- Routes : `/api/` et `/actuator/health` vers core-api, tout le reste vers le frontend. Ni `/v3/api-docs` ni les autres endpoints Actuator ne sont routés vers l'API.
- En-têtes de sécurité sur chaque réponse, erreurs comprises : `Content-Security-Policy` (scripts limités à `'self'`), `X-Content-Type-Options`, `Referrer-Policy`, et `Strict-Transport-Security` en HTTPS.
- IP du client : `X-Forwarded-For` et `X-Real-IP` sont écrasés par l'adresse vue par le gateway, jamais complétés ; `X-Request-Id` est toujours généré par le gateway.
- Limitation par IP : login 10 par minute (rafale de 5), autres `/api/v1/auth/*` 30 par minute (rafale de 10) ; au-delà, 429 en `application/problem+json` (`urn:sentinel:problem:rate-limited`).

## Tests et intégration continue

```bash
bash scripts/check-repo.sh                                         # hygiène du dépôt
bash scripts/check-compose-ports.sh                                # ports publiés en dev et en prod
(cd services/core-api && ./mvnw -B verify)                         # backend (Docker requis)
(cd frontend && npm run lint && npm run typecheck && npm test && npm run build)
bash scripts/e2e.sh                                                # E2E : pile complète vierge + Playwright
```

- `scripts/e2e.sh` démarre une pile vierge sous le projet Compose `vps-sentinel-e2e`, avec ses propres conteneurs et volumes : la pile et la base de développement ne sont jamais touchées. Seul le port 8088 doit être libre. La pile E2E est supprimée à la fin, que les tests réussissent ou non.
- Premier lancement : le script installe les dépendances de `tests/e2e` et le Chromium de Playwright.
- `.github/workflows/ci.yml` exécute ces vérifications sur chaque push et pull request ; les E2E ne démarrent qu'après la réussite des trois autres jobs.

## Production

**Non autorisée en phase 1** : voir [les conditions préalables](docs/deployment/production-prerequisites.md). Pour mémoire, la combinaison prod publie seulement 80 et 443, lit les secrets dans `${SENTINEL_SECRETS_DIR}`, les certificats dans `${SENTINEL_TLS_CERT_DIR}` et le webroot ACME dans `${SENTINEL_ACME_WEBROOT}` :

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --wait
```

## Sécurité de l'API

Détails et justifications : §8 de la [spécification](docs/superpowers/specs/2026-11-02-phase1-foundation-design.md).

- **Authentification :** uniquement `Authorization: Bearer <access token>`, jamais dans l'URL ni par cookie. L'access token est un JWT RS256 de 15 minutes, signé avec `jwt-private.pem`. Sont vérifiés : la signature, l'en-tête `typ: at+jwt`, `iss` (`vps-sentinel`), `aud` (`core-api`), `exp`, `iat`, et la présence et le type de chaque claim. Aucune tolérance de décalage d'horloge.
- **Refus par défaut :** sans jeton, seules ces routes répondent : `GET /actuator/health`, le contrat OpenAPI (dev et test) et `POST /api/v1/auth/{login,refresh,logout}`. Ces trois dernières exigent un en-tête `Origin` présent dans `SENTINEL_ALLOWED_ORIGINS` (format `http(s)://hôte[:port]`, sans chemin ni joker ; une entrée invalide empêche le démarrage). Chaque contrôleur déclare un `@PreAuthorize` ; les permissions viennent du claim `perms`. Il est évalué avant la lecture de la requête : sans la permission, la réponse est 403, même si le corps ou les paramètres sont invalides.
- **Changement de mot de passe imposé :** un jeton `pcr` n'ouvre que `POST /api/v1/auth/change-password`, `POST /api/v1/auth/logout` et `GET /api/v1/me` (méthode et chemin exacts). Tout le reste répond 403 `password-change-required`.
- **Erreurs :** les 401 et 403, comme les requêtes refusées par le pare-feu de Spring Security, sont rendus en Problem Details. La raison d'un refus de jeton n'est jamais renvoyée.
- **Politique des mots de passe :** de 12 à 128 caractères, comptés en points de code Unicode. Différent de l'email, sans tenir compte de la casse ni des espaces. Aucune règle de composition (NIST SP 800-63B).
- **Hachage :** Argon2id au minimum recommandé par l'OWASP (19 Mio, 2 itérations, parallélisme 1). La base refuse tout autre format de hash.
- **Mots de passe temporaires :** 16 caractères tirés par `SecureRandom` d'un alphabet de 56 symboles sans caractères ambigus, soit environ 93 bits d'entropie.
- **Matrice des permissions :** fixe, insérée par migration. `sentinel_app` ne peut que la lire : toute écriture est refusée par PostgreSQL.
- **Sessions :** le refresh token (256 bits, seul son hash SHA-256 est stocké) tourne à chaque refresh. Il expire après 24 h d'inactivité et 7 jours au total. Un jeton déjà tourné présenté de nouveau révoque toute sa session. Le changement de mot de passe révoque toutes les sessions.
- **Limitation des tentatives :** blocage progressif de 30 s à 15 min, à partir du 5ᵉ échec pour un compte depuis une IP et du 20ᵉ pour une IP. Aucun blocage n'est permanent, et un compte reste accessible depuis une autre IP. Les clés de limitation sont des empreintes HMAC : jamais l'identifiant ni l'IP en clair.

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
