# VPS Sentinel — Phase 1 « Foundation » — Spécification de conception

**Date :** 2026-11-02
**Version :** 2.3 (intègre la revue architecturale, les arbitrages Q1 à Q9, les précisions de la phase 0 et celles des jalons M3 et M7)
**Statut :** Validée le 2026-11-02
**Référence :** [TAD v1.0](../../architecture/TAD-v1.0.md). Les renvois « TAD §x » pointent vers ce document ; les renvois « §x » sans préfixe pointent vers la présente spec.

## Historique des révisions

| Version | Date | Changement |
|---|---|---|
| 1 | 2026-11-02 | Première version (commit `ec8065e`) |
| 2 | 2026-11-02 | Intégration des 10 MUST FIX et des SHOULD FIX retenus de la revue, des arbitrages Q1 à Q9 et des précisions sur l'audit des identifiants et la concurrence des refresh tokens. Suivi détaillé en annexe A |
| 2.1 | 2026-11-02 | Validation. JUnit Jupiter 6, version gérée par Spring Boot 4.1 (§4). Précisions issues de la phase 0 : ports de dev publiés sur la boucle locale (§6.3) ; volume PostgreSQL 18 monté sur `/var/lib/postgresql` (§6.1) ; `TEMPORARY` retiré et bases `postgres` et `template1` fermées à `PUBLIC` (§6.5, §13.5) |
| 2.2 | 2026-11-03 | Jalon M3, à la demande de l'utilisateur : `aud = core-api` et `typ: at+jwt` exigés sur l'access token, validation explicite de chaque claim (§8.1, §17) |
| 2.3 | 2026-11-04 | Jalon M7 : l'autorisation précède la lecture de la requête, 403 avant 400 (§8.11) ; intervalle du journal d'audit et conventions du contrat OpenAPI (§10.2) |

---

## 1. Objectif

Livrer le socle de VPS Sentinel : monorepo, Core API Spring Boot, frontend React, PostgreSQL, authentification, gestion des utilisateurs et des rôles, inventaire des VPS, projets et environnements.

**Critère de validation (TAD §13, phase 1) :** un utilisateur autorisé peut enregistrer un VPS et y associer des projets.

Cette phase ne surveille encore rien. Elle pose l'inventaire et la sécurité sur lesquels les phases 2 à 5 s'appuieront : l'agent rattaché à un VPS, les checks HTTP rattachés aux domaines d'un environnement, les incidents rattachés aux VPS et aux projets.

**La phase 1 n'est pas autorisée en production** tant que les conditions du §15 ne sont pas remplies, en premier lieu des sauvegardes et une restauration opérationnelles et testées.

## 2. Décisions de cadrage

| Sujet | Décision |
|---|---|
| Dépôt git | Dépôt indépendant du dépôt `expertiz` (BackofficeService). Répertoire cible `vps-sentinel/`, exclu localement du dépôt parent (§5.2) |
| Autorisation | 5 rôles fixes, matrice de permissions fixe (migration SQL), vue globale : tout utilisateur connecté voit tous les VPS et projets |
| Modèle projet | Projet → Environnements → VPS. Le « déploiement » est le lien environnement ↔ VPS. Les domaines sont portés par l'environnement. Pas d'historique de versions |
| Structure Core API | Monolithe modulaire, frontières vérifiées par Spring Modulith |
| Contrat d'API | Code-first : springdoc génère l'OpenAPI, exporté dans `contracts/openapi/core-api.yaml` et vérifié en CI. Le frontend en génère ses types |
| UI | Mantine |
| Sessions (Q1) | 24 h d'inactivité, 7 jours de durée absolue maximale |
| Migrations (Q2) | Conteneur Flyway indépendant de l'API, avec un rôle propriétaire du schéma. L'API n'a aucun droit sur le schéma |
| Force brute (Q3) | Limitation par couple compte + IP et par IP, avec délai progressif plafonné. Aucun verrouillage de compte déclenchable par un attaquant |
| Mises à jour (Q4) | `PUT` (remplacement complet) pour les VPS, projets et environnements. `PATCH` pour les utilisateurs |
| Production (Q5) | Interdite tant que sauvegardes et restauration ne sont pas opérationnelles et testées (§15) |
| Docker Compose (Q6) | Trois fichiers : commun, dev, prod |
| Décommissionnement (Q8) | Refusé tant qu'il existe des déploiements sur le VPS |
| Package Java (Q9) | `com.expertiz.sentinel.core` |
| Composants différés | RabbitMQ, Redis, VictoriaMetrics, SMTP : introduits par les phases qui en ont besoin |

## 3. Périmètre

**Inclus :**

- Initialisation du monorepo et fichiers d'hygiène (§5.3).
- Docker Compose : `postgres`, `migrate`, `core-api`, `frontend`, `gateway`.
- Rôles PostgreSQL séparés et migrations Flyway dans un conteneur dédié.
- Authentification (login, refresh, logout, changement de mot de passe), limitation des tentatives, création du premier administrateur.
- Gestion des utilisateurs et attribution des rôles.
- CRUD des VPS avec tags.
- CRUD des projets, environnements, domaines et déploiements.
- Journal d'audit et sa consultation.
- Tableau de bord d'inventaire simple.
- Tests (unitaires, intégration, sécurité, concurrence, E2E) et workflow CI.

**Exclus (phases ultérieures) :** agents et leurs credentials, ingestion, métriques, services surveillés, checks, incidents, alertes, emails (dont le « mot de passe oublié » en libre-service), `server_groups`, historique des déploiements, haute disponibilité, sauvegardes automatisées (prérequis de production, §15).

## 4. Stack, versions et prérequis

Versions relevées le 2026-11-02. Le plan d'implémentation les fige et vérifie leur compatibilité mutuelle.

| Composant | Version et contrainte |
|---|---|
| Java | 25 (LTS), `--release 25`. **Un JDK 25 est requis en local** (ou les toolchains Maven) : le JDK 27 installé n'est pas utilisé pour les tests, Spring Boot 4.1 n'étant pas validé dessus |
| Spring Boot | 4.1.x (4.1.2 au moment de la rédaction) |
| Build backend | Wrapper Maven (`mvnw`, mode « script only », sans `.jar` commité) |
| PostgreSQL | 18 |
| Migrations | Flyway : même version pour l'image Docker `flyway/flyway` et pour `flyway-core` côté Java (tests) |
| Node.js | 24 |
| Frontend | Vite 8, React 19, Mantine 9, TanStack Query 5, React Router 8 |
| TypeScript | Dernière version compatible avec `openapi-typescript`, `typescript-eslint` et Vite. TypeScript 7 (compilateur natif) est exclu tant que ces outils ne le prennent pas en charge |
| Client API | `openapi-typescript` + `openapi-fetch` |
| Tests backend | JUnit Jupiter 6 (version gérée par Spring Boot 4.1), Testcontainers dans une version compatible avec Docker Engine 29 (API ≥ 1.44), Spring Modulith test |
| Tests frontend | Vitest, React Testing Library, MSW |
| E2E | Playwright (Chromium) |
| Gateway | Nginx, image officielle stable |

**Conventions :**

- Package Java racine : `com.expertiz.sentinel.core`.
- Identifiants de code en anglais, interface utilisateur en français.
- Commits au format Conventional Commits (`feat:`, `fix:`…).
- Les scripts sont en Bash (Git Bash sous Windows). Sous PowerShell, le wrapper Maven s'appelle `.\mvnw.cmd`.

## 5. Dépôt

### 5.1 Structure

```text
vps-sentinel/                      (racine du dépôt, voir §5.2)
├── .gitattributes
├── .gitignore
├── .editorconfig
├── .env.example
├── docker-compose.yml             commun : services, réseaux, volumes ; aucun port publié
├── docker-compose.dev.yml         dev : gateway HTTP sur 8088, PostgreSQL sur 5432
├── docker-compose.prod.yml        prod : gateway 80/443, certificats, ACME
├── frontend/                      Dockerfile, .dockerignore
├── services/
│   └── core-api/                  Dockerfile, Dockerfile.migrate, .dockerignore, mvnw
│       └── src/main/resources/db/migration/   migrations Flyway (source unique)
├── contracts/
│   └── openapi/core-api.yaml
├── infrastructure/
│   ├── gateway/                   nginx.dev.conf, nginx.prod.conf, security-headers.conf
│   ├── postgres/init/             création des rôles (exécuté par l'image postgres)
│   └── secrets/                   ignoré par git ; dev/ généré par script
├── tests/
│   └── e2e/                       Playwright
├── docs/
│   ├── architecture/              TAD
│   ├── deployment/                procédures de production (§15)
│   └── superpowers/specs/         specs de conception
├── scripts/                       gen-dev-secrets.sh, export-openapi.sh, check-compose-ports.sh
├── .github/workflows/ci.yml
└── README.md
```

Les dossiers `monitoring-service/`, `intelligence-service/`, `agent/`, `contracts/events/`, `contracts/schemas/` et l'infrastructure RabbitMQ, Redis et VictoriaMetrics du TAD §12 seront créés par leurs phases respectives. On ne crée pas de dossiers vides.

### 5.2 Isolation vis-à-vis du dépôt parent et renommage

Le dépôt VPS Sentinel se trouve aujourd'hui dans `expertiz/vps monitoring/`, à l'intérieur du dépôt `expertiz` (BackofficeService), qui le voit comme un dossier non suivi.

Deux opérations sont prévues comme **première étape du plan d'implémentation**, exécutées uniquement avec une validation explicite. Aucune des deux n'est faite pendant la rédaction de la spec :

1. **Renommage** de `vps monitoring/` en `vps-sentinel/`.
   - Le dépôt git n'est pas affecté : le dossier `.git` est déplacé avec le reste.
   - Prérequis : fermer VS Code et les terminaux ouverts sur ce dossier, puis rouvrir l'espace de travail sur le nouveau chemin.
   - Le renommage supprime aussi l'espace dans le chemin, source d'erreurs dans les scripts.
2. **Exclusion locale** : ajout de la ligne `vps-sentinel/` dans `expertiz/.git/info/exclude`.
   - Ce fichier n'est pas versionné : l'historique et les fichiers suivis du dépôt parent ne changent pas.
   - Un `git add .` depuis `expertiz` ne pourra plus embarquer le sous-dépôt.

**Règles permanentes :**

- Toute commande git pour VPS Sentinel s'exécute depuis la racine de `vps-sentinel/`.
- Ne jamais exécuter `git add .` ni `git add -A` depuis `expertiz`.
- Le fichier Compose commun déclare `name: vps-sentinel` : le nom du projet Compose ne dépend plus du nom du dossier.
- Les scripts mettent tous les chemins entre guillemets.

### 5.3 Hygiène du dépôt

Ces fichiers constituent le **premier commit** de l'implémentation, avant tout code :

| Fichier | Contenu |
|---|---|
| `.gitattributes` | `* text=auto eol=lf` ; `*.cmd text eol=crlf` ; `*.bat text eol=crlf` ; `*.png *.jpg *.ico *.woff2 *.jar binary` |
| `.gitignore` | `.env`, `*.pem`, `*.key`, `infrastructure/secrets/`, `target/`, `node_modules/`, `dist/`, `coverage/`, `playwright-report/`, `test-results/`, fichiers d'IDE |
| `.editorconfig` | Fins de ligne LF, UTF-8, indentation par type de fichier |
| `.dockerignore` (un par contexte de build : `services/core-api/`, `frontend/`) | `target/`, `node_modules/`, `dist/`, `.env*`, `*.pem`, `*.key`, `.git` |

**Règles associées :**

- **Bit exécutable :** `git update-index --chmod=+x` sur `mvnw` et `scripts/*.sh`. Les Dockerfiles appellent `sh ./mvnw`, ce qui ne dépend pas de ce bit.
- **Contextes de build :** limités à `services/core-api/` et `frontend/`. La racine du dépôt n'est jamais un contexte de build, donc `.env` n'y entre jamais.
- **Pourquoi c'est nécessaire :** la configuration système de Git active `core.autocrlf=true`. Sans `.gitattributes`, un clone sous Windows produirait des scripts en CRLF. Or les builds Docker copient les fichiers du disque, pas ceux de l'index git, ce qui casserait `mvnw` et les scripts d'initialisation PostgreSQL.

## 6. Infrastructure et déploiement

### 6.1 Services Docker Compose

| Service | Image | Réseaux | Rôle |
|---|---|---|---|
| `postgres` | postgres:18 | data | Base métier, volume persistant `pgdata` monté sur `/var/lib/postgresql` (l'image PostgreSQL 18 refuse l'ancien montage sur `…/data`), création des rôles au premier démarrage (§6.5) |
| `migrate` | image construite avec `services/core-api/Dockerfile.migrate` (base `flyway/flyway`, migrations copiées) | data | Conteneur ponctuel : `flyway migrate` avec le rôle `sentinel_owner`, puis arrêt |
| `core-api` | eclipse-temurin:25-jre (build multi-stage avec `-jdk`) | app, data | API REST, rôle `sentinel_app` |
| `frontend` | nginx (build statique multi-stage) | app | Sert le build Vite, avec `try_files … /index.html` pour les routes du SPA |
| `gateway` | nginx | public, app | Seul service exposé. Terminaison TLS (prod), routage, en-têtes de sécurité, limitation de débit |

**Ordre de démarrage :**

- `migrate` dépend de `postgres` avec `condition: service_healthy` (`pg_isready`).
- `core-api` dépend de `migrate` avec `condition: service_completed_successfully`. Si la migration échoue, l'API ne démarre pas.
- `core-api` expose un healthcheck sur `/actuator/health`.

### 6.2 Réseaux (TAD §9.3)

- `public` : `gateway` uniquement.
- `app` : `gateway`, `frontend`, `core-api`.
- `data` : `postgres`, `migrate`, `core-api`.

PostgreSQL n'est publié que par `docker-compose.dev.yml`. La frontière de confiance pour l'IP du client est l'isolation du réseau `app` (§8.10).

### 6.3 Fichiers Compose (Q6)

Compose additionne les listes `ports` d'un fichier de surcharge au lieu de les remplacer. Le fichier commun ne publie donc aucun port, et chaque environnement ajoute les siens.

| Fichier | Contenu |
|---|---|
| `docker-compose.yml` | `name: vps-sentinel` ; les 5 services, les 3 réseaux, les volumes, les secrets ; **aucun port publié** ; ni configuration Nginx ni certificat |
| `docker-compose.dev.yml` | gateway : `nginx.dev.conf`, HTTP, `127.0.0.1:8088:80` ; postgres : `127.0.0.1:5432:5432` ; ports publiés sur la boucle locale uniquement, jamais sur le réseau local ; secrets lus dans `infrastructure/secrets/dev/` |
| `docker-compose.prod.yml` | gateway : `nginx.prod.conf`, `80:80` et `443:443`, certificats montés depuis `${SENTINEL_TLS_CERT_DIR}`, webroot ACME depuis `${SENTINEL_ACME_WEBROOT}` ; secrets lus dans `${SENTINEL_SECRETS_DIR}` |

Commandes :

- dev : `docker compose -f docker-compose.yml -f docker-compose.dev.yml …`
- prod : `docker compose -f docker-compose.yml -f docker-compose.prod.yml …`

`scripts/check-compose-ports.sh` vérifie, à partir de la sortie de `docker compose … config`, que la combinaison prod ne publie que 80 et 443 sur le gateway, et la combinaison dev seulement 8088 et 5432.

### 6.4 Gateway Nginx

**Routes :**

- `/api/` → `core-api:8080`.
- `/actuator/health` → `core-api:8080/actuator/health`. Aucun autre endpoint Actuator ni `/v3/api-docs` n'est routé.
- `/` → `frontend`.
- Prod, port 80 : `/.well-known/acme-challenge/` est servi depuis le webroot ACME ; tout le reste est redirigé vers HTTPS.

**En-têtes transmis à core-api :**

- `X-Request-Id $request_id` : toujours généré par le gateway, une valeur envoyée par le client est écrasée.
- `X-Forwarded-For $remote_addr` et `X-Real-IP $remote_addr` : écrasés, jamais complétés (§8.10).
- `X-Forwarded-Proto $scheme`.
- `Forwarded ""` : l'en-tête RFC 7239 du client est supprimé.

**Limitation de débit** (grossière, par IP ; la limitation fine est dans l'API, §8.4) :

- `POST /api/v1/auth/login` : 10 requêtes par minute, rafale de 5.
- Autres `/api/v1/auth/*` : 30 requêtes par minute, rafale de 10.
- `limit_req_status 429` (Nginx renvoie 503 par défaut). Une `error_page 429` renvoie un corps `application/problem+json` de type `rate-limited`.

**En-têtes de sécurité :**

- Ils sont définis dans `security-headers.conf`, inclus dans **chaque** bloc `location` avec `always`. En effet, un `add_header` dans un bloc `location` annule tous ceux hérités du bloc `server`.
- Liste des en-têtes :
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'` ;
  - `X-Content-Type-Options: nosniff` ;
  - `Referrer-Policy: strict-origin-when-cross-origin` ;
  - `Strict-Transport-Security: max-age=31536000` (prod uniquement).
- `'unsafe-inline'` est limité aux styles : Mantine injecte des balises `<style>` pour ses variables CSS. Les scripts restent limités à `'self'`.

**Divers :**

- `server_tokens off`.
- `client_max_body_size 1m`.
- Le journal d'accès contient `$request_id`, mais jamais le corps des requêtes, `Authorization` ni `Cookie`.

### 6.5 PostgreSQL : rôles, privilèges et migrations (Q2)

L'image officielle crée `POSTGRES_USER` avec les droits superutilisateur. Ce compte ne sert qu'à l'initialisation et n'est transmis à aucun autre service.

**Rôles :**

| Rôle | Attributs | Utilisé par | Droits |
|---|---|---|---|
| `postgres` (superutilisateur) | — | script `infrastructure/postgres/init/` uniquement | Crée les rôles, la base et le schéma |
| `sentinel_owner` | `LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB` | conteneur `migrate` uniquement | Propriétaire de la base `sentinel` et du schéma `sentinel` |
| `sentinel_app` | `LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB` | `core-api` uniquement | `CONNECT` sur la base `sentinel` seulement, `USAGE` sur le schéma, aucun `CREATE` ni `TEMPORARY`. Droits détaillés ci-dessous |

**Script d'initialisation** (`infrastructure/postgres/init/01-roles.sh`, exécuté par l'image postgres uniquement sur un volume vide) :

- crée les deux rôles avec les mots de passe `SENTINEL_DB_OWNER_PASSWORD` et `SENTINEL_DB_APP_PASSWORD` ;
- crée la base `sentinel` (propriétaire `sentinel_owner`) et le schéma `sentinel` ;
- retire `CONNECT` et `TEMPORARY` à `PUBLIC` sur la base `sentinel`, puis accorde `CONNECT` à `sentinel_app` ;
- retire `CONNECT` et `TEMPORARY` à `PUBLIC` sur les bases `postgres` et `template1` : les rôles applicatifs n'atteignent que la base `sentinel` ;
- `REVOKE ALL ON SCHEMA public FROM PUBLIC` ;
- `ALTER ROLE … SET search_path = sentinel` pour les deux rôles.

Changer un mot de passe par la suite relève d'une procédure manuelle (`ALTER ROLE`), documentée dans `docs/deployment/`.

**Privilèges, posés par les migrations (exécutées par `sentinel_owner`) :**

- **Première migration :**
  - elle commence par `ALTER DEFAULT PRIVILEGES IN SCHEMA sentinel GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app` (et `USAGE` sur les séquences) ;
  - elle accorde **seulement** `SELECT` sur `flyway_schema_history`. Cette table est créée par Flyway avant la première migration et ne reçoit donc pas les privilèges par défaut.
- `roles`, `permissions`, `role_permissions` : `REVOKE INSERT, UPDATE, DELETE … FROM sentinel_app`. La matrice fixe est ainsi protégée au niveau de la base.
- `audit_logs` : `REVOKE UPDATE, DELETE … FROM sentinel_app`. Le rôle n'a jamais `TRUNCATE`, `REFERENCES` ni `TRIGGER`.
- Triggers sur `audit_logs` : `BEFORE UPDATE OR DELETE FOR EACH ROW` et `BEFORE TRUNCATE FOR EACH STATEMENT`, qui lèvent une exception. Ils protègent aussi contre une erreur commise avec le rôle propriétaire.

**Migrations :**

- Source unique : `services/core-api/src/main/resources/db/migration/`.
- `Dockerfile.migrate` copie ce dossier dans une image `flyway/flyway`. Variables du conteneur : `FLYWAY_URL`, `FLYWAY_USER=sentinel_owner`, `FLYWAY_PASSWORD`, `FLYWAY_SCHEMAS=sentinel`, `FLYWAY_CONNECT_RETRIES`.
- **core-api n'exécute jamais de migration.** Au démarrage, une `FlywayMigrationStrategy` appelle `validate()` avec le rôle `sentinel_app` (lecture seule de `flyway_schema_history`). En cas de migration manquante ou en échec, l'API refuse de démarrer.
- Hibernate est en `ddl-auto=validate`.

**Limite assumée :** le rôle propriétaire peut toujours désactiver ou supprimer les triggers, et un administrateur PostgreSQL ou root de l'hôte peut tout modifier. La protection de l'audit vaut contre l'application et contre une compromission du compte `sentinel_app`, pas contre un administrateur privilégié (§9.5).

### 6.6 Configuration et secrets

**Variables d'environnement** (`.env`, non commité ; `.env.example` commité, avec des valeurs factices explicites). Chaque conteneur ne reçoit que les variables dont il a besoin.

| Variable | Reçue par | Rôle |
|---|---|---|
| `POSTGRES_PASSWORD` | postgres | Superutilisateur, initialisation uniquement |
| `SENTINEL_DB_OWNER_PASSWORD` | postgres (init), migrate | Rôle `sentinel_owner` |
| `SENTINEL_DB_APP_PASSWORD` | postgres (init), core-api | Rôle `sentinel_app` |
| `SENTINEL_ALLOWED_ORIGINS` | core-api | Origines autorisées, séparées par des virgules. Prod : l'origine publique. Dev : `http://localhost:5173,http://localhost:8088` |
| `SENTINEL_REFRESH_COOKIE_SECURE` | core-api | `true` par défaut |
| `SENTINEL_BOOTSTRAP_ADMIN_EMAIL`, `SENTINEL_BOOTSTRAP_ADMIN_PASSWORD`, `SENTINEL_BOOTSTRAP_ADMIN_NAME` | core-api | Premier SUPER_ADMIN (§8.7). À retirer après la première connexion |
| `SENTINEL_SECRETS_DIR` | compose (prod) | Répertoire des secrets fichiers |
| `SENTINEL_TLS_CERT_DIR`, `SENTINEL_ACME_WEBROOT` | compose (prod) | Certificats et webroot ACME du gateway |

**Secrets fichiers**, montés via la section `secrets:` de Compose dans `/run/secrets/` :

- `jwt-private.pem`, `jwt-public.pem` : paire RSA 3072 bits pour signer les JWT.
- `hmac.key` : 32 octets aléatoires pour les empreintes HMAC des identifiants et des IP (§8.4, §9.5).

**Sources par environnement :**

- **dev :** `scripts/gen-dev-secrets.sh` génère ces fichiers dans `infrastructure/secrets/dev/`, dossier ignoré par git.
- **Profil `dev` sans secrets :** une paire RSA et une clé HMAC éphémères sont générées au démarrage, avec un avertissement dans les logs.
- **Profil `prod` :** l'API refuse de démarrer si un secret est absent.

### 6.7 Workflow de développement

```bash
./scripts/gen-dev-secrets.sh                                                          # une fois
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres migrate # base + migrations
cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # API sur :8080
cd frontend && npm run dev                                                            # UI sur :5173, proxy /api -> :8080
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build             # pile complète sur :8088
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v                # remise à zéro (base vierge)
```

## 7. Core API — architecture modulaire

### 7.1 Modules

| Module | Package | Responsabilités | Dépend de |
|---|---|---|---|
| `shared` (ouvert) | `…core.shared` | Problem Details, pagination et tri, filtre de corrélation, `Clock`, constantes de permissions (`Permissions`), type `AuditEvent`, empreintes HMAC | — |
| `identity` | `…core.identity` | Utilisateurs, rôles, permissions, authentification, jetons, limitation des tentatives, configuration Spring Security, premier admin | shared |
| `infrastructure` | `…core.infrastructure` | VPS, tags, facettes | shared |
| `projects` | `…core.projects` | Projets, environnements, domaines, déploiements | shared, infrastructure |
| `audit` | `…core.audit` | Écriture et consultation du journal d'audit | shared |

Spring Modulith (`ApplicationModules.verify()`) interdit l'accès aux packages internes d'un autre module et les dépendances cycliques. `shared` est déclaré `OPEN`.

**Frontière avec le futur Monitoring Service (TAD §2.1 et TAD §3.4) :**

- core-api reste propriétaire de l'inventaire.
- Le JWT RS256 autoportant (§8.1) et les UUID stables des VPS, environnements et domaines permettront au Monitoring Service de s'y référer sans dépendance de code.
- Aucun composant des phases 2 à 4 n'est introduit en phase 1.

### 7.2 Interactions et concurrence

- **`projects` → `infrastructure` :** `projects` utilise l'API publique `ServerCatalog` (verrouillage, existence, statut et résumé d'un VPS) pour valider la création d'un déploiement.
- **Pas de dépendance cyclique :** `infrastructure` définit une interface publique `ServerUsageChecker`, que `projects` implémente. `infrastructure` la consulte avant de supprimer un VPS ou de le passer en `DECOMMISSIONED`.
- **Ordre « verrou, puis vérification, puis décision »** (S6) :
  - *création d'un déploiement* : `SELECT … FOR SHARE` sur la ligne du VPS, **puis** relecture de `deleted_at` et `admin_status`, puis insertion. VPS supprimé ou `DECOMMISSIONED` : 409 `server-not-deployable` ;
  - *suppression d'un VPS, ou passage en `DECOMMISSIONED`* : `SELECT … FOR UPDATE` sur la ligne du VPS, **puis** appel de `ServerUsageChecker`. S'il existe au moins un déploiement : 409 `server-in-use`.

  En isolation `READ COMMITTED`, la ligne est relue après l'attente du verrou. Vérifier après le verrou garantit qu'aucun déploiement ne peut viser un VPS en cours de suppression ou de décommissionnement.
- **Ensemble des SUPER_ADMIN** (S5) : toute opération susceptible de réduire le nombre de SUPER_ADMIN actifs (changement de rôle, désactivation, suppression) et le bootstrap (§8.7) prennent d'abord le verrou `pg_advisory_xact_lock` dédié, puis comptent les SUPER_ADMIN actifs. Deux rétrogradations simultanées ne peuvent donc pas faire tomber ce nombre à zéro.
- **Endpoint transverse :** la liste des environnements hébergés par un VPS est servie par `projects` (`GET /servers/{id}/deployments`). Le chemin d'URL n'impose pas le module propriétaire.

### 7.3 Transactions et audit

- Chaque module publie un `AuditEvent(action, targetType, targetId, details)`, défini dans `shared`, via `ApplicationEventPublisher`.
- `audit` l'écoute de façon **synchrone, dans la transaction de l'appelant** (`@EventListener`, et non `@ApplicationModuleListener`, qui est asynchrone).
- **Mutations métier :** l'audit fait partie de la même transaction. Si la transaction est annulée, aucune ligne d'audit n'est écrite ; si elle est validée, la ligne d'audit l'est aussi.
- **Échecs de sécurité** (login refusé, limitation, réutilisation de refresh token, mauvais mot de passe actuel au changement) :
  - l'état de sécurité (compteurs de limitation, révocations) et la ligne d'audit **doivent être validés même si la réponse est une erreur** (400, 401 ou 429) ;
  - le service renvoie donc un résultat typé au lieu de lever une exception dans la méthode transactionnelle. Le contrôleur traduit ce résultat en réponse d'erreur **après** la validation.
- **Acteur de l'audit :**
  - par défaut, il est résolu depuis le contexte de sécurité (claims `sub` et `email` du JWT) ;
  - un `AuditEvent` peut porter un acteur explicite : `ANONYMOUS` pour le login, `SYSTEM` pour le bootstrap et les tâches planifiées, l'utilisateur du jeton pour le refresh et le logout ;
  - l'IP réelle (§8.10) et le `correlationId` (MDC) sont ajoutés par `audit`.
- **Date :** l'horloge applicative est un bean `java.time.Clock` injecté partout, ce qui permet de tester les expirations.

## 8. Authentification et sécurité

### 8.1 Access token

- JWT signé en **RS256**, valable **15 minutes**.
- En-tête `typ: at+jwt` (RFC 9068). Claims : `iss = vps-sentinel`, `aud = core-api`, `sub` (id utilisateur), `email`, `iat`, `exp`, `jti`, `role`, `perms` (permissions du rôle), `pcr` (booléen « changement de mot de passe requis »).
- Validation : signature RS256, `typ`, `iss`, `aud`, `exp` et `iat` obligatoires (`iat` jamais dans le futur), présence et type de chaque claim ; aucune tolérance de décalage d'horloge, l'émetteur et le vérificateur partageant la même horloge.
- Les claims sont **toujours calculés depuis la base** au moment de l'émission (login, refresh, changement de mot de passe), jamais copiés d'un ancien jeton.
- Les autorités Spring Security sont construites depuis `perms`. Le jeton est autoportant : un futur Monitoring Service pourra le valider avec la seule clé publique.
- Validation via `spring-boot-starter-oauth2-resource-server` (décodeur Nimbus, clé publique RSA ; seul RS256 est accepté).
- Autorisation par `@PreAuthorize("hasAuthority('server:write')")` avec les constantes de `Permissions`, refusée par défaut (§8.11).
- Si `pcr = true`, tout endpoint autre que `POST /auth/change-password`, `POST /auth/logout` et `GET /me` répond **403** `password-change-required`.

### 8.2 Refresh token et sessions (Q1)

- Valeur opaque de 256 bits issue de `SecureRandom`, encodée en base64url. Seul son hash SHA-256 est stocké.
- Cookie `sentinel_rt` : `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- **Inactivité :** chaque jeton est valable **24 heures** et tourne à chaque utilisation.
- **Durée absolue :** **7 jours** à partir de `session_started_at`, valeur conservée lors des rotations. Au-delà, une reconnexion est requise.
- `Max-Age` du cookie = minimum entre 24 h et le temps restant avant la limite absolue.
- `POST /auth/refresh` et `POST /auth/logout` exigent un en-tête `Origin` présent dans `SENTINEL_ALLOWED_ORIGINS` (§8.9).

**Algorithme de `POST /auth/refresh`**, exécuté dans une transaction validée quelle que soit l'issue (§7.3) :

1. Cookie absent : 401.
2. **Rotation atomique**, en une instruction :

   ```sql
   UPDATE refresh_tokens
      SET revoked_at = now(), revoked_reason = 'ROTATED', replaced_by = :newId
    WHERE token_hash = :hash
      AND revoked_at IS NULL
      AND expires_at > now()
      AND session_started_at > now() - interval '7 days'
   RETURNING id, user_id, family_id, session_started_at
   ```

3. **Une ligne modifiée :**
   - charger l'utilisateur. S'il n'est pas `ACTIVE` ou s'il est supprimé : révoquer la famille (`USER_DISABLED`), y compris le jeton présenté, qui prend ce motif avec `replaced_by` remis à NULL. Aucun successeur n'est créé. Répondre 401 ;
   - sinon, insérer le nouveau jeton (même `family_id`, même `session_started_at`), émettre un access token dont les claims sont recalculés depuis la base, et répondre 200 avec le même corps que le login.
4. **Aucune ligne modifiée :** relire la ligne par `token_hash`.
   - Introuvable : 401.
   - `revoked_reason = ROTATED` : **réutilisation** (§8.3). Révoquer toute la famille (`REUSE_DETECTED`) et répondre 401.
   - Autre révocation, jeton expiré ou session de plus de 7 jours : 401.

La détection de réutilisation (étape 4) est testée avant l'expiration. Un jeton déjà tourné déclenche donc la révocation de sa famille même s'il a expiré entre-temps.

### 8.3 Concurrence et réutilisation des refresh tokens

**Règle :** un jeton ne produit jamais plus d'un successeur. Toute requête qui présente un jeton déjà tourné est traitée comme une réutilisation, quel que soit le délai écoulé.

**Options étudiées :**

| Option | Principe | Décision |
|---|---|---|
| A. Tolérance temporelle | Pendant N secondes après la rotation, un jeton tourné est accepté ou ignoré sans alerte | **Rejetée.** Si un attaquant tourne le jeton le premier, la requête légitime de la victime tombe dans la fenêtre et n'alerte pas : le vol n'est pas détecté |
| B. Idempotence par rejeu de la réponse | Stocker le jeton successeur chiffré pendant N secondes et le renvoyer à une requête en double | **Rejetée.** Stockage réversible d'un secret, et quiconque présente l'ancien jeton dans la fenêtre obtient le successeur. Même faiblesse que A, avec plus de complexité |
| C. Rotation stricte, sérialisation côté client et métadonnées de diagnostic | Le serveur reste strict ; le client évite les doublons ; l'audit fournit de quoi distinguer un incident bénin d'un vol | **Retenue** |

**Comportement par scénario :**

| Scénario | Comportement | Conséquence |
|---|---|---|
| Deux onglets demandent un refresh en même temps | Le frontend sérialise les refresh entre onglets (Web Locks, §12.2). Le second onglet obtient le verrou après le premier et envoie le cookie déjà renouvelé, puisque le cookie est partagé | Deux rotations successives valides, aucun conflit |
| Deux requêtes concurrentes avec le même jeton | La rotation atomique n'en laisse passer qu'une. La seconde ne modifie aucune ligne, voit `ROTATED` et déclenche la révocation de la famille, ce qui révoque aussi le successeur que la première vient de recevoir | Les deux requêtes perdent la session : reconnexion. Cas rendu rare par la mutualisation dans l'onglet et Web Locks entre onglets |
| La rotation est validée mais la réponse est perdue | Le navigateur garde l'ancien cookie. Le refresh suivant présente un jeton tourné, ce qui révoque la famille | Reconnexion, et une alerte que les métadonnées permettent de classer comme bénigne |
| Une ancienne requête est rejouée | Le jeton est déjà tourné, ce qui révoque la famille si elle est encore active | La session légitime est coupée. Un rejeu suppose d'avoir capturé du trafic TLS ou accédé au poste : couper la session est le comportement voulu |
| Un attaquant présente un ancien jeton | Même traitement. S'il a volé le jeton **courant** et l'utilise le premier, il obtient un successeur ; le refresh suivant de la victime présente alors un jeton tourné, ce qui révoque la famille, successeur de l'attaquant compris | Le vol est contenu. L'access token déjà émis à l'attaquant reste valable au plus 15 minutes |

**Audit de la réutilisation :**

- Une ligne `TOKEN_REUSE_DETECTED` est écrite **seulement si la révocation a coupé au moins un jeton actif**.
- Si la famille était déjà inactive (session terminée, révoquée ou expirée), seul un log `WARN` est émis. Cela évite d'inonder l'audit par des rejeux répétés.
- `details` de l'audit : `familyId`, `secondsSinceRotation`, `sameIp` et `sameUserAgent`. Ces deux derniers champs sont des booléens qui comparent la requête fautive à celle qui a effectué la rotation ; aucune valeur brute n'est stockée.

**Compromis assumé :** les rares cas légitimes (réponse perdue, doublon réseau) coûtent une reconnexion et une alerte classable comme bénigne. En contrepartie, aucun jeton n'est jamais délivré à partir d'un jeton déjà tourné.

### 8.4 Login et limitation des tentatives (Q3)

**Clés de limitation :**

- Les clés ne contiennent jamais l'identifiant ni l'IP en clair. Ce sont des empreintes HMAC (§9.5) :
  - `account_fp = HMAC(hmac.key, "id:" + identifiant normalisé)` ;
  - `ip_fp = HMAC(hmac.key, "ip:" + IP)`.
- L'identifiant est normalisé ainsi : espaces retirés aux extrémités, Unicode NFKC, minuscules (`Locale.ROOT`).
- La limitation s'applique de la même manière que le compte existe ou non. Elle ne révèle donc pas l'existence d'un compte.

**Portées :**

| Portée | Clé | Seuil | Délai |
|---|---|---|---|
| `ACCOUNT_IP` | `account_fp` + `ip_fp` | À partir du 5ᵉ échec | `min(30 s × 2^(n−5), 15 min)` : 30 s, 1 min, 2 min, 4 min, 8 min, puis 15 min |
| `IP` | `ip_fp` | À partir du 20ᵉ échec, tous comptes confondus | `min(30 s × 2^(n−20), 15 min)` |

**Règles :**

- **Compteur :** il est remis à zéro une heure après le dernier échec. Un succès le remet aussi à zéro pour `ACCOUNT_IP`, mais **pas** pour `IP` : un attaquant ne peut pas effacer son compteur en se connectant à son propre compte.
- **Requête pendant un blocage :** réponse **429** `login-throttled` avec `Retry-After`, **avant** toute vérification du mot de passe, ce qui évite aussi le coût d'Argon2. Ces requêtes ne prolongent pas le blocage et ne sont pas auditées individuellement (log et métrique uniquement).
- **Aucun blocage n'est permanent, et aucun blocage par compte seul n'existe.** Un attaquant ne peut pas empêcher un utilisateur légitime de se connecter depuis une autre IP.
- **Mise à jour atomique :** `INSERT … ON CONFLICT … DO UPDATE … RETURNING failure_count, blocked_until`, dans la transaction validée du §7.3.
- **Changement de mot de passe :** la portée `ACCOUNT_IP` de l'utilisateur connecté s'applique aussi. Si elle est bloquée, la réponse est 429 avant toute vérification. Un mauvais mot de passe actuel l'incrémente et produit un audit `PASSWORD_CHANGE_FAILED` (§8.5).
- **Réinitialisation par un admin :** `POST /users/{id}/reset-password` supprime les lignes `ACCOUNT_IP` du compte.
- **Attaque distribuée sur un même compte :** non bloquée, pour ne pas permettre de bloquer un compte à distance. Elle est ralentie par Argon2, la politique de 12 caractères et la limitation par IP, et reste visible dans l'audit. Une alerte automatique est un point ouvert (§17).

**Algorithme de `POST /auth/login {email, password}`**, transaction validée quelle que soit l'issue :

1. Vérifier l'`Origin` et `Content-Type: application/json` (§8.9).
2. Si une portée applicable est bloquée : 429 `login-throttled`.
3. Rechercher l'utilisateur non supprimé par identifiant normalisé.
4. Selon le résultat :
   - **Utilisateur introuvable :** vérification Argon2 factice pour égaliser le temps de réponse. Échec `UNKNOWN_IDENTIFIER`.
   - **Mot de passe faux :** échec `BAD_PASSWORD`.
   - **Mot de passe correct, compte `DISABLED` :** audit `LOGIN_FAILED` avec le motif `ACCOUNT_DISABLED`, puis 401, sans incrément.
   - **Mot de passe correct, mot de passe temporaire expiré** (`password_expires_at` dépassé) : audit `LOGIN_FAILED` avec le motif `TEMP_PASSWORD_EXPIRED`, puis 401, sans incrément.
5. Pour un échec `UNKNOWN_IDENTIFIER` ou `BAD_PASSWORD` :
   - incrémenter les portées ;
   - auditer `LOGIN_FAILED` (§9.5 pour le contenu) ;
   - si un blocage commence, auditer `LOGIN_THROTTLED` ;
   - répondre 401.
6. **Succès :**
   - remettre à zéro la portée `ACCOUNT_IP` ;
   - mettre à jour `last_login_at` par un `UPDATE` direct qui **n'incrémente pas** `version` ;
   - créer une nouvelle famille de refresh tokens (`session_started_at = now()`) ;
   - auditer `LOGIN_SUCCEEDED` ;
   - répondre 200 :

```json
{
  "accessToken": "…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "…", "email": "…", "fullName": "…",
    "role": "DEVOPS", "permissions": ["server:read", "…"],
    "mustChangePassword": false
  }
}
```

**Messages :**

- Toutes les réponses 401 du login portent le même message : **« Identifiants invalides. »**
- Les réponses 429 portent : **« Trop de tentatives. Réessayez dans N secondes. »**

### 8.5 Logout et changement de mot de passe

**`POST /auth/logout` :**

- révoque toute la famille du cookie (`LOGOUT`) et efface le cookie ;
- fonctionne même avec un access token expiré ;
- répond 204, y compris sans cookie.

**`POST /auth/change-password {currentPassword, newPassword}`**, authentifié :

- si la portée `ACCOUNT_IP` est bloquée : 429 `login-throttled` (§8.4) ;
- si le mot de passe actuel est faux : incrément de la limitation, audit `PASSWORD_CHANGE_FAILED`, puis 400 `validation-error` sur le champ `currentPassword`. Pas de 401, qui signifierait une session expirée et déclencherait un refresh côté client ;
- vérifie la politique du §8.6, et que le nouveau mot de passe diffère de l'actuel ;
- révoque **tous** les refresh tokens de l'utilisateur (`PASSWORD_CHANGED`) ;
- remet `must_change_password` à `false` et `password_expires_at` à `NULL` ;
- émet une nouvelle paire de jetons, avec le même corps de réponse que le login ;
- audite `PASSWORD_CHANGED`.

### 8.6 Mots de passe et mots de passe temporaires

**Hachage :** **Argon2id** (`Argon2PasswordEncoder`, BouncyCastle), via `DelegatingPasswordEncoder` avec `argon2` par défaut.

**Politique :**

- 12 caractères minimum, 128 maximum (comptés en points de code) ;
- différent de l'email (comparaison insensible à la casse) ;
- aucune autre règle de composition.

**Mots de passe temporaires** (création et réinitialisation par un admin) :

- générés par `SecureRandom` : 16 caractères tirés d'un alphabet de 56 symboles sans caractères ambigus (pas de `0`, `O`, `1`, `l` ni `I`), soit environ 93 bits d'entropie ;
- valables **72 heures** (configurable) : `password_expires_at = now() + 72 h`. Au-delà, le login est refusé (§8.4) et l'admin doit réinitialiser ;
- `must_change_password = true` ;
- renvoyés **une seule fois**, dans le corps de la réponse ; jamais placés dans une URL ni dans un log ;
- les en-têtes anti-cache par défaut de Spring Security (`Cache-Control: no-cache, no-store, max-age=0, must-revalidate`) sont conservés sur toute l'API, y compris login et refresh.

**Mot de passe de bootstrap :** il n'expire pas (`password_expires_at = NULL`). Il est choisi par l'opérateur et n'est pas transmis à un tiers. Le changement reste forcé à la première connexion.

### 8.7 Premier administrateur

La condition est évaluée **à chaque démarrage** de core-api, sous le verrou consultatif des SUPER_ADMIN (§7.2) :

- s'il existe un SUPER_ADMIN actif, les variables sont ignorées, sans erreur ;
- si `SENTINEL_BOOTSTRAP_ADMIN_EMAIL` ou `SENTINEL_BOOTSTRAP_ADMIN_PASSWORD` est absente, rien n'est fait ;
- si le mot de passe ne respecte pas la politique du §8.6, l'erreur est journalisée et rien n'est fait ;
- si l'email appartient déjà à un utilisateur non supprimé, quel que soit son rôle ou son statut, l'erreur est journalisée et rien n'est fait : ni promotion, ni réactivation ;
- sinon, le compte est créé avec `must_change_password = true`, avec un audit `USER_CREATED` d'acteur `SYSTEM`.

Aucun de ces cas ne bloque le démarrage. Le README demande de retirer les variables de bootstrap après la première connexion.

### 8.8 Garde-fous de gestion des utilisateurs

- Impossible de désactiver, supprimer ou retirer le rôle `SUPER_ADMIN` du **dernier** SUPER_ADMIN actif : 409 `last-super-admin`. La vérification se fait sous verrou (§7.2).
- Un utilisateur ne peut ni modifier son propre rôle, ni se désactiver, ni se supprimer : 409 `self-modification-forbidden`.
- Désactiver, supprimer ou changer le rôle d'un utilisateur révoque tous ses refresh tokens (`USER_DISABLED` ou `ROLE_CHANGED`).
- Son access token en cours reste valable au plus 15 minutes. C'est un compromis assumé, conforme au TAD §8.1 : le JWT n'est pas le seul mécanisme de révocation.

### 8.9 CSRF, CORS et XSS

**Modèle d'authentification :**

- Les endpoints métier ne sont authentifiés **que** par `Authorization: Bearer`. Un site tiers ne peut pas forger cet en-tête : ces endpoints ne sont pas exposés au CSRF.
- Seuls `/auth/login`, `/auth/refresh` et `/auth/logout` **acceptent** le cookie de session comme preuve d'authentification.
- `/auth/change-password`, authentifié par Bearer, émet un nouveau cookie dans sa réponse, mais ignore celui qu'il reçoit.

**Protection des endpoints de session** (trois défenses cumulées) :

1. `SameSite=Strict` sur le cookie.
2. `Origin` obligatoire et présent dans `SENTINEL_ALLOWED_ORIGINS`, sinon 403 `origin-not-allowed`. Cela couvre aussi le CSRF de login, qui connecterait la victime au compte de l'attaquant. Conséquence : ces endpoints ne sont pas utilisables par un client non navigateur, ce qui convient puisque la phase 1 n'en a pas.
3. Les endpoints qui ont un corps exigent `Content-Type: application/json`, sinon 415.

**CSRF par jeton de Spring Security :** désactivé, avec la justification ci-dessus. Toute nouvelle authentification par cookie devra revoir cette section.

**CORS :** aucune configuration. Les réponses ne portent pas d'en-tête `Access-Control-Allow-Origin`, et le navigateur bloque toute requête cross-origin.

**XSS :**

- Un script injecté peut utiliser l'access token en mémoire et appeler `/auth/refresh` lui-même. Le cookie `HttpOnly` empêche seulement de l'exfiltrer.
- La défense repose donc sur la prévention :
  - CSP stricte sur les scripts (§6.4) ;
  - échappement automatique de React ;
  - interdiction de `dangerouslySetInnerHTML` (règle ESLint `react/no-danger` en erreur) ;
  - affichage des champs libres en texte brut ;
  - `repository_url` limité à http(s) et ouvert avec `rel="noopener noreferrer"`.

### 8.10 Adresse IP réelle du client

- Le gateway **écrase** `X-Forwarded-For` et `X-Real-IP` avec `$remote_addr`, renseigne `X-Forwarded-Proto` et supprime `Forwarded` (§6.4).
- core-api active `server.forward-headers-strategy=native` (valve RemoteIp de Tomcat). Les proxies de confiance sont les plages privées par défaut.
- **Frontière de confiance :** core-api n'est joignable que depuis le réseau `app` (aucun port publié en prod). Seul le gateway peut donc lui présenter un `X-Forwarded-For`, et il l'a écrasé.
- L'IP obtenue alimente `audit_logs.ip`, `refresh_tokens.ip` et la limitation (§8.4).
- En dev sans gateway (API sur `localhost:8080`), l'IP est celle du poste local, ce qui est acceptable.

### 8.11 Configuration Spring Security

- Sans état (aucune session HTTP) ; CSRF désactivé (§8.9) ; aucune configuration CORS.
- `oauth2ResourceServer().jwt()` avec la clé publique RSA.
- `@EnableMethodSecurity`.
- **Refus par défaut :** chaque endpoint a un `@PreAuthorize` ou est explicitement public (login, refresh, logout, health). Un test parcourt tous les endpoints enregistrés et échoue si l'un d'eux n'a pas d'entrée dans la matrice du §13.4.
- **Autorisation avant la lecture de la requête :** le `@PreAuthorize` du contrôleur est évalué avant que Spring MVC lise le corps, les paramètres et les variables de chemin. Sans la permission, la réponse est 403 `forbidden` même si la requête est invalide : un 400 révélerait les règles de validation d'un endpoint interdit. Seule une expression qui lit un argument de la méthode (`#nom`) attend la lecture de la requête ; aucun endpoint de l'API n'en utilise.
- Les 401 et 403 générés par Spring Security passent par un `AuthenticationEntryPoint` et un `AccessDeniedHandler` qui produisent du Problem Details (§11).
- Filtres dédiés : contrôle de l'`Origin` sur `/auth/login|refresh|logout`, et contrôle de `pcr` (§8.1).
- En-têtes de sécurité par défaut de Spring Security conservés, dont l'anti-cache.
- Actuator : seul `health` est exposé, avec `show-details=never`.
- springdoc : actif en dev et en test (pour le contrat), désactivé en prod (`springdoc.api-docs.enabled=false`).

### 8.12 Matrice des permissions

Fixe, insérée par migration Flyway, protégée en écriture au niveau de la base (§6.5), sans écran de modification :

| Permission | SUPER_ADMIN | INFRA_ADMIN | DEVOPS | DEVELOPER | VIEWER |
|---|:-:|:-:|:-:|:-:|:-:|
| `server:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `server:write` | ✓ | ✓ | ✓ | – | – |
| `server:delete` | ✓ | ✓ | – | – | – |
| `project:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `project:write` | ✓ | ✓ | ✓ | ✓ | – |
| `project:delete` | ✓ | ✓ | ✓ | – | – |
| `user:read` | ✓ | ✓ | – | – | – |
| `user:manage` | ✓ | – | – | – | – |
| `audit:read` | ✓ | ✓ | – | – | – |

- `server:write` couvre la création et la modification des VPS, changement de `admin_status` compris.
- `project:write` couvre la création et la modification des projets et environnements, ainsi que l'ajout et le retrait de domaines et de déploiements.
- `project:delete` couvre la suppression d'un projet ou d'un environnement.

## 9. Modèle de données

### 9.1 Conventions

- **Schéma :** `sentinel`.
- **Clés primaires :** `uuid`, **générées par l'application** en UUIDv7 (générateur Hibernate ou bibliothèque dédiée), de sorte que l'id est connu avant l'insertion. La colonne garde `DEFAULT uuidv7()` comme filet pour les insertions SQL des migrations.
- **Dates :** `created_at` et `updated_at` en `timestamptz NOT NULL DEFAULT now()`, en UTC (`hibernate.jdbc.time_zone=UTC`). `updated_at` est maintenu par l'application.
- **Verrouillage optimiste :** `version bigint NOT NULL DEFAULT 0` (`@Version` JPA) sur `users`, `servers`, `projects`, `environments`. Les champs techniques de connexion (`last_login_at`) sont mis à jour sans incrémenter `version` (§8.4).
- **Suppression logique :** `deleted_at timestamptz` sur `users`, `servers`, `projects`, `environments`. Les unicités fonctionnelles passent par des index uniques partiels `WHERE deleted_at IS NULL`.
- **Contraintes nommées explicitement** (`uq_servers_name_active`, …) pour traduire une violation d'unicité en erreur de champ (§11).
- **Énumérations :** colonnes `varchar` avec contrainte `CHECK`.
- **Schéma :** Hibernate en `ddl-auto=validate`. Seul le conteneur `migrate` modifie le schéma (§6.5).

### 9.2 Identity

**`users`**

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| email | varchar(254) | NOT NULL, stocké normalisé (§10.3) ; unique parmi les non supprimés |
| full_name | varchar(120) | NOT NULL |
| password_hash | varchar(255) | NOT NULL |
| password_expires_at | timestamptz | NULL ; renseigné pour un mot de passe temporaire (§8.6) |
| status | varchar(16) | NOT NULL, `ACTIVE` \| `DISABLED` |
| must_change_password | boolean | NOT NULL DEFAULT true |
| last_login_at | timestamptz | NULL ; mis à jour sans incrément de `version` |
| created_at, updated_at, deleted_at, version | | voir §9.1 |

**`roles`** : `id`, `code varchar(32) UNIQUE`, `label varchar(64)`. Les 5 lignes sont insérées par migration. Lecture seule pour `sentinel_app`.

**`permissions`** : `id`, `code varchar(64) UNIQUE`, `description varchar(255)`. Les 9 lignes sont insérées par migration. Lecture seule pour `sentinel_app`.

**`role_permissions`** : `role_id`, `permission_id`, PK composite. La matrice du §8.12 est insérée par migration. Lecture seule pour `sentinel_app`.

**`user_roles`** : `user_id` (FK users), `role_id` (FK roles), PK `(user_id, role_id)` et **`UNIQUE(user_id)`** (un seul rôle par utilisateur en phase 1).

**`refresh_tokens`**

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK users, NOT NULL, indexé |
| family_id | uuid | NOT NULL, indexé |
| token_hash | char(64) | NOT NULL UNIQUE (SHA-256 hex) |
| session_started_at | timestamptz | NOT NULL |
| issued_at | timestamptz | NOT NULL |
| expires_at | timestamptz | NOT NULL (`issued_at` + 24 h) |
| revoked_at | timestamptz | NULL |
| revoked_reason | varchar(32) | NULL : `ROTATED` \| `LOGOUT` \| `REUSE_DETECTED` \| `USER_DISABLED` \| `ROLE_CHANGED` \| `PASSWORD_CHANGED` \| `PASSWORD_RESET` |
| replaced_by | uuid | NULL |
| user_agent | varchar(255) | NULL |
| ip | inet | NULL |

**`login_throttles`**

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| scope | varchar(16) | NOT NULL, `ACCOUNT_IP` \| `IP` |
| account_fp | char(32) | NULL pour `IP`, NOT NULL pour `ACCOUNT_IP` (empreinte HMAC tronquée à 128 bits, en hexadécimal) ; indexé |
| ip_fp | char(32) | NOT NULL (empreinte HMAC) |
| failure_count | int | NOT NULL |
| first_failure_at | timestamptz | NOT NULL |
| last_failure_at | timestamptz | NOT NULL |
| blocked_until | timestamptz | NULL |
| | | Unicité sur `(scope, coalesce(account_fp, ''), ip_fp)` |

**Tâches planifiées quotidiennes** (instance unique en phase 1) :

- suppression des `refresh_tokens` dont `session_started_at < now() − 14 jours`. Au-delà de la durée absolue de 7 jours, aucun jeton de la famille ne peut plus être valide ;
- suppression des `login_throttles` sans échec depuis 24 h et sans blocage en cours.

### 9.3 Infrastructure

**`servers`**

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| name | varchar(100) | NOT NULL ; unique sur `lower(name)` parmi les non supprimés |
| hostname | varchar(253) | NULL |
| public_ip | inet | NULL |
| private_ip | inet | NULL |
| provider | varchar(100) | NULL (texte libre, auto-complété) |
| region | varchar(100) | NULL (texte libre, auto-complété) |
| os_family | varchar(16) | NULL, `UBUNTU` \| `DEBIAN` \| `RHEL` \| `ROCKY` \| `ALMALINUX` \| `CENTOS` \| `OTHER` |
| os_version | varchar(32) | NULL |
| cpu_cores | smallint | NULL, de 1 à 1024 |
| ram_mb | int | NULL, de 1 à 16 777 216 |
| disk_gb | int | NULL, de 1 à 1 048 576 |
| admin_status | varchar(16) | NOT NULL DEFAULT `ACTIVE`, `ACTIVE` \| `MAINTENANCE` \| `DECOMMISSIONED` |
| description | varchar(2000) | NULL |
| created_at, updated_at, deleted_at, version | | voir §9.1 |

**`server_tags`** : `server_id` (FK servers), `tag varchar(50)`, PK `(server_id, tag)`. Les tags sont normalisés et validés selon le §10.3. Ils sont remplacés en bloc par `PUT /servers/{id}`.

### 9.4 Projets

**`projects`** : `id`, `name varchar(100)` (unique sur `lower(name)` parmi les non supprimés), `description varchar(2000)`, `repository_url varchar(500)`, plus les colonnes du §9.1.

**`environments`** : `id`, `project_id` (FK projects, NOT NULL), `name varchar(50)` (unique sur `(project_id, lower(name))` parmi les non supprimés), `kind varchar(16)` (`PRODUCTION` \| `STAGING` \| `DEVELOPMENT` \| `OTHER`), plus les colonnes du §9.1.

**`domains`**

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| environment_id | uuid | FK environments, NOT NULL |
| hostname | varchar(253) | NOT NULL ; stocké normalisé (§10.3) ; unique au niveau global |
| is_primary | boolean | NOT NULL DEFAULT false ; au plus un domaine principal par environnement (index unique partiel `(environment_id) WHERE is_primary`) |
| created_at | timestamptz | |

**`deployments`** (lien environnement ↔ VPS)

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| environment_id | uuid | FK environments, NOT NULL |
| server_id | uuid | FK servers, NOT NULL, indexé |
| role | varchar(50) | NULL (« web », « db », « worker »…) |
| created_at | timestamptz | |
| created_by | uuid | NULL (id utilisateur) |
| | | `UNIQUE(environment_id, server_id)` |

Un déploiement ne peut pas viser un VPS supprimé ou `DECOMMISSIONED` : 409 `server-not-deployable`, vérifié sous verrou (§7.2).

### 9.5 Audit

**`audit_logs`** (en ajout seul)

| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| occurred_at | timestamptz | NOT NULL DEFAULT now(), indexé (desc) |
| actor_type | varchar(16) | NOT NULL, `USER` \| `SYSTEM` \| `ANONYMOUS` |
| actor_user_id | uuid | NULL, indexé (pas de FK : le journal reste indépendant) |
| actor_email | varchar(254) | NULL (copie figée de l'email d'un utilisateur **authentifié** ; jamais une saisie de login) |
| action | varchar(64) | NOT NULL, indexé |
| target_type | varchar(32) | NULL |
| target_id | uuid | NULL ; index `(target_type, target_id)` |
| details | jsonb | NOT NULL DEFAULT `'{}'` |
| ip | inet | NULL (IP réelle, §8.10) |
| correlation_id | varchar(64) | NULL |

**Protection (§6.5) :**

- `sentinel_app` ne peut qu'insérer et lire ; aucun `UPDATE`, `DELETE` ni `TRUNCATE`.
- Des triggers rejettent `UPDATE`, `DELETE` et `TRUNCATE`.
- **Portée :** c'est une protection contre l'application et contre une compromission de `sentinel_app`. Elle **ne garantit rien** contre un administrateur PostgreSQL ou un utilisateur root de l'hôte. La preuve d'intégrité (chaînage par hash, export hors de l'hôte) est un point ouvert (§17).

**Contenu de `details` :**

- **Liste blanche par entité :** seuls les champs déclarés auditables sont enregistrés. Pour une modification : `{"changes": {"<champ>": {"from": …, "to": …}}}`. Pour une suppression : un **instantané complet** des champs auditables de l'élément supprimé, ce qui permet de reconstituer domaines et déploiements supprimés physiquement.
- **Jamais enregistrés :** mot de passe, hash, jeton, cookie, clé, mot de passe temporaire.

**Identifiants saisis lors d'une authentification échouée :**

- La valeur brute saisie dans le champ identifiant **n'est jamais stockée**, même si elle contient `@` : un utilisateur peut y avoir tapé son mot de passe.
- À la place, `details.identifierFingerprint` contient `HMAC-SHA256(hmac.key, "id:" + identifiant normalisé)`, tronqué à 128 bits.
  - Elle permet de corréler les tentatives répétées sur un même identifiant.
  - Elle ne peut pas être inversée, ni testée par dictionnaire sans la clé.
- Si l'identifiant correspond à un compte existant, `target_type = USER` et `target_id` portent ce compte. L'acteur reste `ANONYMOUS` et `actor_email` reste NULL.
- **Limites assumées :**
  - après une rotation de `hmac.key`, les empreintes anciennes et nouvelles ne sont plus comparables ;
  - en cas de fuite de la clé, un attaquant pourrait tester des candidats contre les empreintes.
- Les logs applicatifs ne contiennent pas non plus l'identifiant saisi.

**Actions auditées :**

| Domaine | Actions | Détails principaux |
|---|---|---|
| Session | `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGIN_THROTTLED`, `LOGOUT`, `TOKEN_REUSE_DETECTED`, `PASSWORD_CHANGED`, `PASSWORD_CHANGE_FAILED` | `reason` (`UNKNOWN_IDENTIFIER` \| `BAD_PASSWORD` \| `ACCOUNT_DISABLED` \| `TEMP_PASSWORD_EXPIRED`), `identifierFingerprint`, `scope`, `failureCount`, `blockedSeconds`, `familyId`, `secondsSinceRotation`, `sameIp`, `sameUserAgent` |
| Utilisateurs | `USER_CREATED`, `USER_UPDATED`, `USER_ROLE_CHANGED`, `USER_DISABLED`, `USER_ENABLED`, `USER_DELETED`, `USER_PASSWORD_RESET` | Diffs ou instantanés selon la liste blanche ; `throttlesCleared` pour la réinitialisation |
| VPS | `SERVER_CREATED`, `SERVER_UPDATED`, `SERVER_DELETED` | idem |
| Projets | `PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_DELETED`, `ENVIRONMENT_CREATED`, `ENVIRONMENT_UPDATED`, `ENVIRONMENT_DELETED`, `DOMAIN_ADDED`, `DOMAIN_REMOVED`, `DEPLOYMENT_ADDED`, `DEPLOYMENT_REMOVED` | idem |

La rétention de l'audit est illimitée en phase 1, conformément au TAD §5.4.

### 9.6 Règles de suppression et de cycle de vie

| Entité | Effet |
|---|---|
| Utilisateur | Suppression logique, statut `DISABLED`, révocation des jetons. Garde-fous du §8.8 |
| VPS, suppression | Refusée (409 `server-in-use`) tant qu'il existe des déploiements ; sinon suppression logique. Les tags sont conservés. Sous verrou (§7.2) |
| VPS, passage en `DECOMMISSIONED` (Q8) | Refusé (409 `server-in-use`) tant qu'il existe des déploiements. Sous verrou (§7.2) |
| Projet | Dans une même transaction : suppression logique du projet et de ses environnements, suppression physique de leurs domaines et déploiements, un audit par élément avec instantané |
| Environnement | Suppression logique ; suppression physique de ses domaines et déploiements ; audits avec instantané |
| Domaine, déploiement | Suppression physique, auditée avec instantané |

## 10. API REST

Préfixe `/api/v1`. Format JSON. L'OpenAPI est exposé par springdoc sur `/v3/api-docs` en dev et en test, jamais routé par le gateway, et exporté dans `contracts/openapi/core-api.yaml`.

### 10.1 Endpoints

| Méthode et chemin | Permission | Remarques |
|---|---|---|
| `POST /auth/login` | public (`Origin` contrôlé) | §8.4 ; 200, 401 ou 429 |
| `POST /auth/refresh` | cookie (`Origin` contrôlé) | §8.2, §8.3 |
| `POST /auth/logout` | cookie (`Origin` contrôlé) | §8.5 |
| `POST /auth/change-password` | authentifié | §8.5 |
| `GET /me` | authentifié | Profil et permissions |
| `GET /users` | `user:read` | Filtres `q` (nom ou email), `role`, `status` |
| `POST /users` | `user:manage` | `{email, fullName, role}` → 201 `{user, temporaryPassword, temporaryPasswordExpiresAt}` |
| `GET /users/{id}` | `user:read` | |
| `PATCH /users/{id}` | `user:manage` | `{fullName?, role?, status?, version}` ; un champ absent reste inchangé ; `null` est refusé (400) |
| `DELETE /users/{id}` | `user:manage` | 204 |
| `POST /users/{id}/reset-password` | `user:manage` | 200 `{temporaryPassword, temporaryPasswordExpiresAt}`. Révoque les jetons (`PASSWORD_RESET`), remet `must_change_password` à vrai, supprime les limitations du compte |
| `GET /servers` | `server:read` | Filtres `q` (nom, hostname, IP), `tag`, `provider`, `region`, `adminStatus` |
| `POST /servers` | `server:write` | 201 |
| `GET /servers/{id}` | `server:read` | |
| `PUT /servers/{id}` | `server:write` | Remplacement complet, avec `version` et `tags` (liste complète) ; 409 `server-in-use` si passage en `DECOMMISSIONED` avec des déploiements |
| `DELETE /servers/{id}` | `server:delete` | 204, ou 409 `server-in-use` |
| `GET /servers/facets` | `server:read` | `{providers[], regions[], tags[]}` (valeurs distinctes existantes) |
| `GET /servers/{id}/deployments` | `project:read` | Environnements hébergés, avec le projet associé |
| `GET /projects` | `project:read` | Filtre `q` ; chaque élément inclut le nombre d'environnements |
| `POST /projects` | `project:write` | 201 |
| `GET /projects/{id}` | `project:read` | Inclut les environnements, leurs domaines et leurs déploiements (avec nom et statut du VPS) |
| `PUT /projects/{id}` | `project:write` | Remplacement complet, avec `version` |
| `DELETE /projects/{id}` | `project:delete` | 204 |
| `POST /projects/{id}/environments` | `project:write` | 201 |
| `PUT /environments/{id}` | `project:write` | Remplacement complet, avec `version` |
| `DELETE /environments/{id}` | `project:delete` | 204 |
| `POST /environments/{id}/domains` | `project:write` | `{hostname, isPrimary}` → 201. Si `isPrimary`, l'ancien domaine principal perd ce statut dans la même transaction |
| `DELETE /domains/{id}` | `project:write` | 204 |
| `POST /environments/{id}/deployments` | `project:write` | `{serverId, role?}` → 201, ou 409 `server-not-deployable` / `deployment-exists` |
| `DELETE /deployments/{id}` | `project:write` | 204 |
| `GET /audit-logs` | `audit:read` | Filtres `actorUserId`, `action`, `targetType`, `targetId`, `from`, `to` |

### 10.2 Conventions

**Pagination :**

- paramètres `?page=0&size=20&sort=<champ>,<asc|desc>` ;
- `page ≥ 0` et `1 ≤ size ≤ 100`. Hors bornes : 400 `validation-error` ;
- réponse `{"items": [...], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3}`.

**Tri (liste blanche) :** un champ hors liste renvoie 400 `validation-error`.

| Endpoint | Champs triables | Tri par défaut |
|---|---|---|
| `GET /users` | `fullName`, `email`, `createdAt`, `lastLoginAt` | `fullName,asc` |
| `GET /servers` | `name`, `adminStatus`, `provider`, `region`, `createdAt` | `name,asc` |
| `GET /projects` | `name`, `createdAt` | `name,asc` |
| `GET /audit-logs` | `occurredAt` | `occurredAt,desc` |

**`PUT` (Q4) :**

- Le corps contient la représentation complète des champs modifiables, plus `version`.
- Un champ facultatif absent ou `null` est **vidé**. Les champs obligatoires sont validés.
- Une `version` qui ne correspond plus renvoie 409 `version-conflict`.

**`PATCH` (utilisateurs uniquement) :** aucun champ n'y est vidable, donc un champ absent reste inchangé et `null` est refusé. Pas de « troisième état » à gérer.

**Journal d'audit :** les filtres se combinent ; `from` est inclus et `to` exclu, si bien que deux intervalles consécutifs ne comptent jamais deux fois la même ligne ; `to` doit être postérieur à `from`. Les égalités de date sont départagées par id, dans le sens du tri.

**Contrat OpenAPI (`contracts/openapi/core-api.yaml`) :**

- Il ne décrit que `/api/**`. Schéma de sécurité `bearer` (JWT) global, retiré (`security: []`) pour login, refresh et logout.
- Chaque opération déclare son statut de succès (200, 201 ou 204) et une réponse `default` Problem Details (§11).
- Identifiants d'opération `<ressource><Méthode>` (par exemple `serversCreate`) et clés triées : le fichier ne dépend ni du port ni de l'ordre de découverte des contrôleurs.
- Les champs exigés seulement en `PUT` (`version`, et `adminStatus` des VPS) n'y sont pas déclarés obligatoires ; leur description donne la règle.

**Autres règles :**

- Bean Validation sur des DTO distincts des entités JPA. Toutes les entrées sont validées selon le §10.3.
- Dates en ISO-8601 UTC (`2026-11-02T10:30:00Z`).

### 10.3 Règles de validation

Règle générale : les chaînes sont débarrassées de leurs espaces aux extrémités. Les caractères de contrôle Unicode sont refusés, sauf `\n` et `\t` dans les descriptions. Une chaîne vide après ce nettoyage équivaut à `null`.

| Champ | Règle |
|---|---|
| `email` | Normalisé (Unicode NFKC, minuscules) ; 254 caractères max ; forme `local@domaine`, le domaine contenant au moins un point |
| `fullName` | 1 à 120 caractères |
| mot de passe | §8.6 |
| `servers.name` | 1 à 100 caractères, `^[A-Za-z0-9][A-Za-z0-9 ._-]*$` |
| `servers.hostname` | Minuscules ; RFC 1123 ; 253 caractères max ; segments de 1 à 63 caractères `[a-z0-9-]`, sans tiret au début ni à la fin |
| `public_ip`, `private_ip` | Adresse IPv4 ou IPv6 littérale |
| `provider`, `region` | 100 caractères max |
| `os_family` | Énumération du §9.3 |
| `os_version` | 32 caractères max, `^[A-Za-z0-9._ -]+$` |
| `cpu_cores`, `ram_mb`, `disk_gb` | Bornes du §9.3 |
| `description` | 2000 caractères max ; affichée en texte brut |
| tags | 20 max par VPS ; chacun normalisé en minuscules, 1 à 50 caractères, `^[a-z0-9][a-z0-9._-]*$` ; doublons supprimés |
| `projects.name` | 1 à 100 caractères |
| `repository_url` | 500 caractères max ; URL absolue `http` ou `https` uniquement ; aucun identifiant intégré (`user:pass@`) |
| `environments.name` | 1 à 50 caractères, `^[A-Za-z0-9][A-Za-z0-9._-]*$` |
| `kind` | Énumération du §9.4 |
| `domains.hostname` | Règles de `servers.hostname`, avec au moins un point, sans point final, sans joker ; les noms internationalisés sont acceptés uniquement en punycode (`xn--`) |
| `deployments.role` | Facultatif ; 50 caractères max, `^[A-Za-z0-9][A-Za-z0-9 ._-]*$` |
| `version` | Obligatoire pour `PUT` et `PATCH` |

## 11. Erreurs, logs et traçabilité

**Problem Details (RFC 9457)**, `Content-Type: application/problem+json`, via `ProblemDetail` de Spring, un `@RestControllerAdvice` dans `shared`, ainsi que l'`AuthenticationEntryPoint` et l'`AccessDeniedHandler` du §8.11 :

```json
{
  "type": "urn:sentinel:problem:validation-error",
  "title": "Requête invalide",
  "status": 400,
  "detail": "Un ou plusieurs champs sont invalides.",
  "instance": "/api/v1/servers",
  "correlationId": "3f2a…",
  "errors": [{ "field": "name", "message": "ne doit pas être vide" }]
}
```

**Types de problèmes** (le champ `type` vaut `urn:sentinel:problem:<code>`) :

| Code | Statut | Origine |
|---|---|---|
| `validation-error` | 400 | Validation, pagination, tri, mot de passe actuel incorrect (§8.5) |
| `unauthorized` | 401 | Jeton absent, invalide ou expiré ; échec de login ou de refresh |
| `forbidden` | 403 | Permission manquante |
| `origin-not-allowed` | 403 | `Origin` absent ou non autorisé (§8.9) |
| `password-change-required` | 403 | `pcr = true` (§8.1) |
| `not-found` | 404 | |
| `unsupported-media-type` | 415 | `Content-Type` autre que JSON |
| `version-conflict` | 409 | Verrouillage optimiste |
| `last-super-admin` | 409 | §8.8 |
| `self-modification-forbidden` | 409 | §8.8 |
| `server-in-use` | 409 | Suppression ou décommissionnement d'un VPS qui a des déploiements |
| `server-not-deployable` | 409 | VPS supprimé ou `DECOMMISSIONED` |
| `deployment-exists` | 409 | Couple environnement–VPS déjà lié |
| `duplicate` | 409 | Violation d'unicité, traduite en erreur de champ grâce au nom de contrainte |
| `login-throttled` | 429 | Limitation applicative (§8.4), avec `Retry-After` |
| `rate-limited` | 429 | Limitation du gateway (§6.4) |
| `internal-error` | 500 | Sans aucun détail technique |

**Règles :**

- Les messages d'erreur ne reprennent jamais une valeur saisie sur les endpoints d'authentification.
- **Corrélation :**
  - un filtre lit `X-Request-Id`, ou en génère un s'il est absent (dev sans gateway) ;
  - il le place dans le MDC sous `correlationId` ;
  - il le renvoie dans l'en-tête de réponse et dans le corps des erreurs ;
  - `audit` l'enregistre.

  Le `correlationId` est une chaîne opaque de 64 caractères au plus (le `$request_id` de Nginx compte 32 caractères hexadécimaux). Voir l'écart avec le TAD §4.3 au §16.
- **Logs :**
  - JSON structuré (journalisation structurée native de Spring Boot) en profil `prod`, texte lisible en `dev` ;
  - aucun mot de passe, jeton, hash, cookie, clé ni identifiant de login saisi n'est journalisé ;
  - les corps de requête ne sont pas journalisés.

## 12. Frontend

### 12.1 Organisation

```text
frontend/src/
├── api/            schema.d.ts (généré), client openapi-fetch, middleware d'auth
├── auth/           AuthProvider, garde-fous de routes, refresh, coordination entre onglets
├── features/
│   ├── servers/
│   ├── projects/
│   ├── users/
│   ├── audit/
│   └── dashboard/
├── components/     composants partagés (tableaux paginés, filtres, confirmations)
├── layout/         AppShell, navigation
└── main.tsx, router.tsx, theme.ts
```

### 12.2 Authentification côté client

- L'access token et l'utilisateur courant sont gardés **en mémoire** dans `AuthProvider`, jamais dans `localStorage` ni `sessionStorage`.
- Au chargement, un appel à `POST /auth/refresh` est fait : en cas de succès, la session est restaurée ; sinon, redirection vers `/login`.
- **Middleware `openapi-fetch` :**
  - il ajoute `Authorization: Bearer` ;
  - sur une réponse 401, il lance **un seul** refresh, partagé par toutes les requêtes de l'onglet, puis rejoue la requête une fois ;
  - si le refresh échoue, la session est vidée et l'utilisateur est redirigé vers `/login` ;
  - `/auth/login`, `/auth/refresh` et `/auth/logout` sont **exclus** de cette logique, pour éviter les boucles. `/auth/change-password` y reste soumis comme tout endpoint authentifié : un access token expiré pendant la saisie est renouvelé de façon transparente.
- **Coordination entre onglets :**
  - **Web Locks :** chaque refresh s'exécute sous le verrou `navigator.locks.request('sentinel-refresh', …)`, avec un délai maximal de 10 s (`AbortController`). Si `navigator.locks` est indisponible, seule la mutualisation dans l'onglet s'applique, sans plantage.
  - **Contexte sécurisé :** seuls HTTPS et `localhost` sont pris en charge, car ce sont les seuls contextes où Web Locks et les cookies `Secure` fonctionnent.
  - **Déconnexion :** elle est diffusée par `BroadcastChannel('sentinel-auth')`. Les autres onglets vident leur session et redirigent vers `/login`.
- Si `mustChangePassword`, ou sur une réponse 403 `password-change-required` : redirection forcée vers `/change-password`.
- Sur une réponse 429 au login, le message affiche un compte à rebours basé sur `Retry-After`.

### 12.3 Pages

| Route | Accès | Contenu |
|---|---|---|
| `/login` | public | Formulaire email et mot de passe ; gestion des 401 et 429 |
| `/change-password` | authentifié | Changement forcé ou volontaire |
| `/` | authentifié | Inventaire : nombre de VPS par statut administratif, nombre de projets et, uniquement pour les rôles qui ont `audit:read`, les 10 dernières actions du journal. Assemblé à partir des endpoints de liste (`totalItems`). L'appel à `/audit-logs` n'est pas émis sans la permission |
| `/servers` | `server:read` | Tableau paginé, filtres et tri synchronisés avec l'URL |
| `/servers/new`, `/servers/:id/edit` | `server:write` | Formulaire VPS (envoyé complet en `PUT`) ; tags, fournisseur et région auto-complétés via `/servers/facets` |
| `/servers/:id` | `server:read` | Fiche VPS et environnements hébergés |
| `/projects` | `project:read` | Liste paginée |
| `/projects/:id` | `project:read` | Fiche projet, un onglet par environnement (domaines, déploiements) ; ajouts dans des modales |
| `/users` | `user:read` | Liste ; création, modification et réinitialisation si `user:manage` |
| `/audit` | `audit:read` | Journal filtrable |
| `/profile` | authentifié | Profil, lien vers le changement de mot de passe |

### 12.4 Comportements transverses

- **Mise en page :** `AppShell` de Mantine.
  - Navigation : Tableau de bord, VPS, Projets, Utilisateurs\*, Audit\* (\* masqués sans la permission).
  - Menu utilisateur (profil, déconnexion) et bascule clair/sombre.
- **Permissions :** les routes et les actions sont masquées selon `permissions`. C'est du confort d'interface : le backend reste l'autorité.
- **Erreurs :**
  - les `errors[]` des Problem Details sont mappés sur les champs du formulaire (`@mantine/form`) ;
  - les autres erreurs s'affichent en notification ;
  - un 409 `version-conflict` affiche « Cet élément a été modifié par quelqu'un d'autre. Rechargez la page. »
- **Mot de passe temporaire :** il s'affiche une fois, avec un bouton de copie et sa date d'expiration. Il est retiré du cache TanStack Query à la fermeture de la modale (`mutation.reset()`).
- **Suppressions :** toute suppression demande une confirmation.
- **Affichage :** les champs libres sont affichés en texte brut ; `dangerouslySetInnerHTML` est interdit.
- **Types d'API :** générés par `npm run gen:api` (`openapi-typescript ../contracts/openapi/core-api.yaml`). Le fichier généré est commité.

## 13. Stratégie de tests

### 13.1 Principes

- **PostgreSQL réel obligatoire** (Testcontainers PostgreSQL 18) pour tout ce qui touche la persistance. Ni H2, ni base en mémoire, ni repository simulé dans les tests d'intégration.
  - Le conteneur de test exécute **le même script d'initialisation** que la prod (§6.5).
  - Il applique les migrations avec `sentinel_owner` (`flyway-core`).
  - L'application s'y connecte avec `sentinel_app`.
- Doivent impérativement tourner sur PostgreSQL réel : migrations, droits des rôles, triggers et `TRUNCATE`, index uniques partiels et fonctionnels, `uuidv7()`, `inet` et `jsonb`, verrous `FOR SHARE` / `FOR UPDATE`, verrous consultatifs, `INSERT … ON CONFLICT`, unicité du domaine principal.
- Expirations et délais testés avec un `Clock` contrôlé, jamais avec `sleep`.
- Tests de concurrence : N fils lancés simultanément (`CountDownLatch`), assertions sur l'état final en base.

### 13.2 Unitaires (backend)

- Politique de mot de passe.
- Générateur de mots de passe temporaires : longueur, alphabet, usage de `SecureRandom`.
- Calcul des délais progressifs (bornes, plafond de 15 min).
- Normalisation des identifiants et calcul des empreintes HMAC (séparation des domaines `id:` et `ip:`).
- Liste blanche et calcul des diffs d'audit.
- Normalisation et validation des tags, hostnames, domaines et URL (tables de cas).
- Logique des garde-fous du §8.8.

### 13.3 Intégration backend (par fonctionnalité)

**Login :**

- succès ;
- mot de passe faux ;
- identifiant inconnu (temps de réponse comparable) ;
- compte désactivé ;
- mot de passe temporaire expiré ;
- refus pour `pcr = true` sur un endpoint métier.

**Limitation des tentatives :**

- 4 échecs sans blocage, puis délais du 5ᵉ au 10ᵉ échec, plafond de 15 min (avec `Clock`) ;
- **aucun blocage permanent** ;
- indépendance des couples compte + IP : une IP attaquante bloquée n'empêche pas le même compte depuis une autre IP ;
- portée `IP` au 20ᵉ échec ;
- requêtes pendant un blocage : 429 sans vérification du mot de passe ni prolongation ;
- remise à zéro par un succès (`ACCOUNT_IP` seulement) et par la réinitialisation admin ;
- 10 échecs en parallèle donnent un compteur exact.

**Refresh :**

- rotation ;
- **N refresh parallèles avec le même cookie : exactement une réponse 200**, puis famille révoquée ;
- réutilisation d'un jeton tourné, même expiré : famille révoquée et audit unique ;
- rejeu sur une famille inactive : aucune ligne d'audit ;
- inactivité de 24 h ;
- durée absolue de 7 jours ;
- utilisateur désactivé ;
- claims recalculés après un changement de rôle ;
- attributs du cookie (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path`) et `Max-Age` au minimum de 24 h et du temps restant.

**Protections des endpoints de session :**

- `Origin` absent ou étranger : 403 sur login, refresh et logout ;
- `Content-Type` non JSON : 415 ;
- aucune réponse ne porte d'en-tête `Access-Control-Allow-Origin`.

**Jetons falsifiés :** `alg=none`, mauvaise signature, confusion RS256/HS256, jeton expiré, mauvais émetteur. Tous donnent 401 en Problem Details.

**Logout et changement de mot de passe :** révocations ; mot de passe actuel faux (400 sur `currentPassword`, limitation incrémentée, audit) ; 429 si la portée est bloquée.

**Bootstrap :** les cas du §8.7 (création, admin existant, variables absentes, mot de passe faible, email existant).

**Utilisateurs :**

- CRUD et `PATCH` (`null` refusé) ;
- garde-fous ;
- **deux rétrogradations simultanées des deux derniers SUPER_ADMIN : il en reste au moins un** ;
- mot de passe temporaire : 72 h, puis refus.

**VPS :**

- CRUD et sémantique de `PUT` (champ facultatif vidé) ;
- `version-conflict` ;
- suppression et décommissionnement refusés avec des déploiements ;
- **création d'un déploiement et suppression du VPS simultanées : jamais de déploiement sur un VPS supprimé**.

**Projets, environnements, domaines, déploiements :**

- CRUD ;
- bascule du domaine principal ;
- suppression en cascade avec instantanés d'audit ;
- `server-not-deployable` et `deployment-exists`.

**Pagination, tri et validation :** pagination, liste blanche de tri (400), tableau de validation du §10.3 piloté par des cas.

**Problem Details :** format de toutes les erreurs, y compris les 401 et 403 de Spring Security et les violations d'unicité traduites en champ.

**IP réelle :** `X-Forwarded-For` en provenance d'un proxy de confiance enregistré ; valeur falsifiée ignorée (simulation de la valve).

### 13.4 Autorisation

- **Matrice :** un test paramétré vérifie, pour chaque endpoint du §10.1 et chaque rôle, le statut attendu (succès ou 403), selon la matrice du §8.12.
  - Il couvre aussi : 401 sans jeton, et 403 `password-change-required` pour chaque endpoint non exempté.
- **Refus par défaut :** le test énumère les endpoints enregistrés (`RequestMappingHandlerMapping`) et échoue si l'un d'eux n'a pas d'entrée dans la matrice.

### 13.5 Base de données et audit

**Droits de `sentinel_app`** (PostgreSQL réel) :

- échec de `UPDATE`, `DELETE` et `TRUNCATE` sur `audit_logs` ;
- échec de `INSERT`, `UPDATE` et `DELETE` sur `roles`, `permissions` et `role_permissions` ;
- échec de `UPDATE` sur `flyway_schema_history` ;
- échec de `CREATE TABLE`, `ALTER TABLE` et `DROP TABLE` ;
- échec de `CREATE TEMP TABLE` et de la connexion aux bases `postgres` et `template1`.

**Triggers :** rejet de `UPDATE`, `DELETE` et `TRUNCATE`, y compris avec le rôle propriétaire.

**Atomicité :**

- une mutation annulée ne laisse aucune ligne d'audit ;
- un échec de login ou une réutilisation de jeton laissent l'audit et l'état de sécurité **validés malgré le 401**.

**Confidentialité de l'audit :**

- Login avec des identifiants qui sont en réalité des mots de passe, avec et sans `@` (`S3cr3t-Passw0rd!`, `p@ssw0rd-1234567`).
- Assertions :
  - aucune colonne de `audit_logs` (`details` compris, en texte) ne contient la chaîne ;
  - les logs applicatifs capturés ne la contiennent pas non plus ;
  - l'empreinte est stable pour une même saisie.

**Instantanés :** la suppression d'un projet produit les instantanés des domaines et déploiements supprimés.

### 13.6 Modulith et contrat

- `ApplicationModules.of(…).verify()`.
- Un test récupère `/v3/api-docs.yaml` et le compare à `contracts/openapi/core-api.yaml`. Il échoue en cas de différence. `scripts/export-openapi.sh` régénère le fichier.

### 13.7 Frontend (`npm run lint && npm run typecheck && npm test && npm run build`)

Vitest + React Testing Library + MSW :

- middleware d'auth : un seul refresh pour plusieurs 401 simultanés, `/auth/login`, `/auth/refresh` et `/auth/logout` exclus, un seul nouvel essai, échec qui redirige ;
- Web Locks utilisé quand il est disponible, repli sans plantage sinon ;
- déconnexion propagée par `BroadcastChannel` ;
- 429 au login avec compte à rebours ;
- garde-fous de routes ;
- formulaires VPS et projet (envoi complet en `PUT`, mapping des erreurs serveur) ;
- mot de passe temporaire affiché une fois puis retiré du cache ;
- règle ESLint `react/no-danger` active.

Vérification que `schema.d.ts` est à jour : `npm run gen:api && git diff --exit-code`.

### 13.8 E2E (`tests/e2e`, Playwright, Chromium)

Exécution sur la pile dev (`http://localhost:8088`), précédée d'un `down -v` pour partir d'une base vierge.

**E2E-1, parcours administrateur :**

1. Connexion avec l'admin initial (variables de bootstrap).
2. Changement de mot de passe forcé.
3. Création du VPS `VPS-01` (fournisseur, région, OS, tags).
4. Création du projet `Demo` avec un environnement `prod` (kind `PRODUCTION`) et le domaine `demo.example.com`.
5. Rattachement de l'environnement `prod` à `VPS-01` (rôle « web »).
6. Vérifications :
   - la fiche `VPS-01` liste `Demo / prod` ;
   - le journal d'audit contient les actions correspondantes, avec une IP et un `correlationId`.
7. Tentative de suppression de `VPS-01`, puis de passage en `DECOMMISSIONED` : refus avec le message « VPS utilisé par des déploiements ».

**E2E-2, utilisateur en lecture seule :**

1. L'admin crée un utilisateur `VIEWER` et copie le mot de passe temporaire.
2. Connexion de cet utilisateur, puis changement de mot de passe forcé.
3. Les VPS et projets sont visibles ; aucun bouton de création, de modification ou de suppression ; les menus Utilisateurs et Audit sont absents.
4. Un appel direct à `POST /api/v1/servers` avec son jeton renvoie 403.

**Contrôles du gateway** (requêtes API Playwright) :

- en-têtes de sécurité présents sur `/` et sur `/api/*` ;
- un `X-Forwarded-For` falsifié lors d'un login échoué n'apparaît pas dans l'IP auditée ;
- le dépassement de débit sur `/api/v1/auth/login` renvoie 429 au format `application/problem+json`.

### 13.9 Infrastructure

- `scripts/check-compose-ports.sh` (§6.3) : ports publiés par les combinaisons dev et prod.
- `git ls-files --eol` : tous les fichiers texte en LF, sauf `*.cmd` et `*.bat`.
- `git ls-files -s` : mode `100755` sur `mvnw` et `scripts/*.sh`.

### 13.10 CI (`.github/workflows/ci.yml`)

Quatre jobs, sur push et sur pull request :

- **checks** : vérifications du §13.9.
- **backend** : `setup-java` 25, `./mvnw verify`. Docker est disponible sur `ubuntu-latest` pour Testcontainers.
- **frontend** : `npm ci`, lint, typecheck, tests, build, vérification de `gen:api`.
- **e2e** : génération des secrets de dev, build et démarrage de la pile dev, `npx playwright test`. Il ne s'exécute qu'après la réussite des trois autres.

Le workflow est commité, mais ne s'exécutera qu'une fois le dépôt poussé sur GitHub.

## 14. Critères d'acceptation

### 14.1 Globaux

1. Le premier commit contient les fichiers du §5.3. Les vérifications du §13.9 passent.
2. Depuis un clone propre, la pile dev démarre sans erreur, avec ces trois commandes :

   ```bash
   cp .env.example .env
   ./scripts/gen-dev-secrets.sh
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
   ```

   - `migrate` se termine avec le code 0 avant le démarrage de `core-api` ;
   - `postgres`, `core-api`, `frontend` et `gateway` sont en bonne santé.
3. Avec la combinaison commun + prod, seul le gateway publie des ports (80, 443), vérifié par `check-compose-ports.sh`.
4. Le premier SUPER_ADMIN est créé depuis les variables de bootstrap, et le changement de mot de passe est forcé.
5. Les scénarios E2E-1 et E2E-2 et les contrôles du gateway (§13.8) passent.
6. `./mvnw verify` est vert : matrice et refus par défaut, Modulith, contrat OpenAPI, droits des rôles, concurrence, confidentialité de l'audit. Les vérifications frontend du §13.7 sont vertes.
7. Chaque événement du §9.5 produit une ligne d'audit avec l'acteur, la cible, l'IP réelle et le `correlationId`, sans aucune donnée secrète.
8. Le README indique que la phase 1 n'est pas autorisée en production et renvoie au §15.

### 14.2 Par fonctionnalité

| Id | Fonctionnalité | Critère vérifiable | Tests |
|---|---|---|---|
| AC-AUTH-1 | Login | Un compte actif se connecte ; les échecs renvoient tous le même 401 ; un compte désactivé ou un mot de passe temporaire expiré sont refusés | §13.3 Login |
| AC-AUTH-2 | Limitation | Délais progressifs du 5ᵉ échec (`ACCOUNT_IP`) et du 20ᵉ (`IP`), plafond de 15 min, jamais permanents ; l'utilisateur légitime n'est pas bloqué depuis une autre IP | §13.3 Limitation |
| AC-AUTH-3 | Refresh | Rotation atomique : une seule réussite en concurrence ; réutilisation : famille révoquée ; comportements du §8.3 | §13.3 Refresh |
| AC-AUTH-4 | Sessions | Fin de session après 24 h d'inactivité ou 7 jours au total | §13.3 Refresh |
| AC-AUTH-5 | CSRF / CORS | `Origin` et `Content-Type` imposés sur les endpoints de session ; aucun en-tête CORS ; endpoints métier uniquement en Bearer | §13.3 Protections |
| AC-AUTH-6 | Mot de passe | Politique appliquée ; changement forcé ; révocation des sessions au changement | §13.3 Logout et changement |
| AC-USR-1 | Utilisateurs | CRUD, garde-fous (y compris en concurrence), mot de passe temporaire de 72 h affiché une fois, limitations remises à zéro par la réinitialisation | §13.3 Utilisateurs, §13.7 |
| AC-SRV-1 | VPS | CRUD avec `PUT` complet, conflits de version, suppression et décommissionnement refusés tant que le VPS est utilisé, cohérence en concurrence | §13.3 VPS |
| AC-PRJ-1 | Projets | CRUD des projets et environnements ; un seul domaine principal ; un déploiement unique par couple ; cascade avec instantanés | §13.3 Projets |
| AC-AUD-1 | Audit | Toutes les actions du §9.5 tracées ; aucun secret ni identifiant brut ; ajout seul imposé par la base | §13.5 |
| AC-SEC-1 | Autorisation | Matrice respectée sur tous les endpoints ; aucun endpoint sans règle | §13.4 |
| AC-SEC-2 | Gateway | En-têtes de sécurité, IP réelle non falsifiable, 429 au format JSON | §13.8 |
| AC-DASH-1 | Tableau de bord | Compteurs égaux aux `totalItems` des listes ; bloc audit seulement avec `audit:read`, sans appel émis sinon | §13.7, E2E-2 |
| AC-OPS-1 | Infrastructure | Rôles séparés, migrations hors de l'API, ports conformes, fichiers d'hygiène présents | §13.5, §13.9 |

## 15. Conditions préalables à la mise en production (Q5)

La phase 1 **n'est pas autorisée en production**. Le passage en production exige, au minimum, que les points suivants soient faits et vérifiés :

1. **Sauvegardes PostgreSQL** automatisées, avec rétention définie, stockées hors de l'hôte (TAD §9.4).
2. **Restauration testée :** restauration complète sur un environnement vierge, avec un procès-verbal daté.
3. **Procédure de reprise après incident** documentée dans `docs/deployment/` (TAD §9.4).
4. **TLS :** émission et renouvellement Let's Encrypt validés, via le webroot ACME (§6.4) et un rechargement de Nginx après chaque renouvellement.
5. **Secrets de production :** génération des clés JWT et HMAC et des mots de passe des rôles, stockage hors du dépôt, procédure de rotation.
6. **Bootstrap :** variables retirées après la première connexion de l'administrateur.
7. **Watchdog externe** sur l'endpoint public (TAD §9.5), ou acceptation explicite et datée de son absence.

Ces éléments ne font pas partie de l'implémentation de la phase 1. Ils feront l'objet d'une spec dédiée avant tout déploiement.

## 16. Écarts par rapport au TAD

| Élément du TAD | Décision en phase 1 | Justification | Impact futur |
|---|---|---|---|
| TAD §5.1 table `project_servers` | Non créée : le lien se déduit de projet → environnements → `deployments` | Éviter une donnée redondante qui pourrait diverger | Les phases suivantes interrogent `deployments` |
| TAD §3.3 Projects, « URLs » | Non modélisées ; seuls les domaines le sont | Les URL à vérifier relèvent des checks de la phase 3 | La phase 3 rattachera ses URL de check aux environnements et domaines |
| TAD §5.1 `server_groups` | Remplacé par `server_tags` | Les tags suffisent au regroupement en phase 1 | Introduction possible si les besoins dépassent les tags |
| TAD §3.3 Infrastructure, « Fournisseurs, Régions » | Texte libre avec auto-complétion | Pas de tables de référence à maintenir | Tables de référence possibles plus tard, alimentées par les valeurs existantes |
| TAD §5.3 Redis (verrous temporaires, états transitoires) | Limitation des tentatives et verrous dans PostgreSQL | Ne pas déployer Redis pour un seul usage ; cohérence transactionnelle avec l'audit | Déplacement possible vers Redis quand il sera introduit |
| TAD §5.1 `user_roles` | Un seul rôle par utilisateur (`UNIQUE(user_id)`) | Rôles hiérarchiques ; multi-rôle inutile | Supprimer la contrainte pour autoriser plusieurs rôles |
| TAD §5.1 tables d'identité | Ajout de `role_permissions` et `login_throttles` | Matrice fixe et limitation des tentatives | — |
| TAD §8.1 « Protection contre les attaques par force brute » | Délai progressif par couple compte + IP et par IP, sans verrouillage de compte | Q3 : pas de blocage déclenchable par un attaquant | Alerte sur les attaques distribuées (§17) |
| TAD §4.3 `correlationId: "uuid"` | Chaîne opaque de 64 caractères au plus (le `$request_id` de Nginx compte 32 caractères hexadécimaux) | Identifiant natif du gateway, sans transformation | L'enveloppe d'événement de la phase 4 le transportera tel quel ; le TAD §4.3 sera précisé |
| TAD §9.1 services Docker | Monitoring Service, RabbitMQ, VictoriaMetrics et Redis non déployés ; ajout d'un service ponctuel `migrate` | Phases ultérieures ; Q2 | Ajout des services avec leurs phases |
| TAD §12 structure du monorepo | Racine `vps-sentinel/`, seulement les dossiers de la phase 1, trois fichiers Compose au lieu d'un | §5.1, Q6 | Les dossiers des phases suivantes seront créés à leur tour |

## 17. Points ouverts pour les phases suivantes

Relevés dans le TAD ou dans la revue, hors périmètre de la phase 1. Ils sont listés pour ne pas être perdus :

- **Phase 2 et ingestion :**
  - le critère de la phase 2 (« un VPS transmet ses données au Control Plane ») suppose un endpoint d'ingestion, que la roadmap place en phase 3 ;
  - il faudra soit livrer un endpoint d'ingestion minimal en phase 2, soit fusionner le critère.
- **Enrôlement des agents :** le mécanisme de première identification (jeton d'enrôlement à usage unique, généré depuis la fiche VPS ?) est à concevoir en phase 2.
- **RabbitMQ :** le Monitoring Service publie des événements (TAD §3.4), mais RabbitMQ arrive en phase 4. Il faut décider si la phase 3 fonctionne en mémoire, ou avancer RabbitMQ.
- **Sondes externes (TAD §6.3) et watchdog indépendant (TAD §9.5) :** aucune phase de la roadmap ne les porte. Le watchdog est aussi une condition de production (§15).
- **Clés JWT :**
  - distribution de la clé publique au Monitoring Service (fichier partagé ou endpoint JWKS) ;
  - rotation des clés. Le `kid` émis est déjà l'empreinte RFC 7638 de la clé. Chaque service aura son propre `aud` : core-api n'accepte que `core-api` depuis la phase 1.

  Phase 3.
- **Routage du gateway par service :** convention à fixer avant l'arrivée du Monitoring Service, pour éviter les conflits de chemins sous `/api/v1` (phase 3).
- **Synchronisation de l'inventaire vers le Monitoring Service :** appel REST ou événements (phases 3 et 4).
- **Sémantique de `admin_status = MAINTENANCE`** face aux `maintenance_windows` du TAD (phase 5).
- **Sauvegardes PostgreSQL (TAD §9.4) :** non couvertes par la phase 1 ; condition de production (§15).
- **Mot de passe oublié en libre-service :** phase 5, avec SMTP.
- **Alerte sur attaque distribuée** contre un même compte (seuil d'échecs multi-IP) : phase 5, avec les notifications.
- **Preuve d'intégrité de l'audit :** chaînage par hash et export hors de l'hôte. Avant la production si la politique de l'entreprise l'exige.
- **Durcissements facultatifs relevés par la revue :**
  - préfixe `__Secure-` pour le cookie ;
  - liste de mots de passe courants ;
  - refus, en profil `prod`, des valeurs d'exemple de `.env.example` ;
  - verrou de tâches planifiées (ShedLock) si plusieurs instances.

## Annexe A — Suivi de la revue du 2026-11-02

**MUST FIX** (tous intégrés) :

| Id | Sujet | Sections |
|---|---|---|
| M1 | Rôles PostgreSQL séparés, protection de l'audit | §6.5, §9.5, §13.5 |
| M2 | Rotation atomique des refresh tokens | §8.2, §8.3, §13.3 |
| M3 | Échecs d'authentification validés malgré 401 et 429 | §7.3, §8.4, §13.5 |
| M4 | Modèle CSRF, CORS, XSS | §8.9, §8.11 |
| M5 | Mots de passe temporaires : expiration, génération, anti-cache | §8.6, §9.2, §10.1 |
| M6 | IP réelle via le gateway | §6.4, §8.10, §13.3, §13.8 |
| M7 | Trois fichiers Compose, critères mis à jour | §6.3, §14.1 |
| M8 | `.gitattributes`, `.gitignore`, `.dockerignore`, bit exécutable, secrets | §5.3, §6.6, §13.9 |
| M9 | Écarts avec le TAD | §16 |
| M10, avec la précision A | Aucun identifiant brut ; empreinte HMAC ; tests | §8.4, §9.5, §13.5 |

**SHOULD FIX :**

| Id | Décision | Sections |
|---|---|---|
| S1 | **Non retenu tel quel.** Remplacé par l'analyse de la précision B : rotation stricte, sérialisation côté client, métadonnées de diagnostic, sans fenêtre de tolérance | §8.3 |
| S2 | Intégré : repli sans Web Locks, déconnexion entre onglets, exclusion des endpoints de session du refresh automatique | §12.2, §13.7 |
| S3 | Intégré : compteurs de connexion sortis de `users`, `last_login_at` mis à jour sans incrémenter `version` | §8.4, §9.1, §9.2 |
| S4 | Remplacé par Q3 : délai progressif, aucun verrouillage ; la réinitialisation supprime les limitations | §8.4 |
| S5 | Intégré : verrou consultatif des SUPER_ADMIN, test en concurrence | §7.2, §8.8, §13.3 |
| S6 | Intégré : Q8 et l'ordre « verrou, puis vérification » | §7.2, §9.6 |
| S7 | Intégré : UUIDv7 générés par l'application | §9.1 |
| S8 | Intégré : CSP complétée, en-têtes inclus par `location`, 429 au format JSON | §6.4 |
| S9 | Intégré : liste blanche, instantanés, portée de la protection explicitée. Le chaînage par hash est reporté (§17) | §9.5 |
| S10 | Remplacé par Q4 : `PUT` et `PATCH` | §10.1, §10.2 |
| S11 | Intégré : liste blanche de tri, bornes de pagination | §10.2 |
| S12 | Intégré : tableau de validation | §10.3 |
| S13 | Intégré : refus par défaut, Problem Details pour les 401 et 403 | §8.11, §13.4 |
| S14 | Intégré : tests critiques, `Clock`, PostgreSQL réel, critères par fonctionnalité | §13, §14.2 |
| S15 | Intégré : JDK 25 en local, TypeScript compatible, Testcontainers compatible avec Docker 29 | §4 |
| S16 | Partiel : webroot ACME prévu dans la configuration prod ; la procédure de renouvellement est une condition de production | §6.4, §15 |
| S17 | Remplacé par Q5 : conditions préalables à la production | §15 |
| S18 | Intégré : `name: vps-sentinel`, chemins entre guillemets ; renommage et exclusion locale planifiés | §5.2 |

**OPTIONAL de la revue :**

- absorbés parce qu'ils découlaient directement d'un autre changement :
  - O4 (`Max-Age` du cookie, §8.2) ;
  - O7 (format du `correlationId`, §11 et §16) ;
  - O8 (limitation de tout `/auth/*`, audit `PASSWORD_CHANGE_FAILED`, §6.4 et §8.4) ;
  - O9 (springdoc et Actuator, §8.11) ;
  - O10 (bootstrap, §8.7) ;
  - O14 (Chromium, §13.8) ;
- les autres sont listés au §17.
