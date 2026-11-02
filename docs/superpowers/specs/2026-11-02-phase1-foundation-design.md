# VPS Sentinel — Phase 1 « Foundation » — Spécification de conception

**Date :** 2026-11-02
**Statut :** À relire
**Référence :** [TAD v1.0](../../architecture/TAD-v1.0.md) (les renvois « TAD §x » pointent vers ce document)

---

## 1. Objectif

Livrer le socle de VPS Sentinel : monorepo, Core API Spring Boot, frontend React, PostgreSQL, authentification, gestion des utilisateurs et des rôles, inventaire des VPS, projets et environnements.

**Critère de validation (TAD §13, phase 1) :** un utilisateur autorisé peut enregistrer un VPS et y associer des projets.

Cette phase ne surveille encore rien. Elle pose l'inventaire et la sécurité sur lesquels les phases 2 à 5 s'appuieront : l'agent rattaché à un VPS, les checks HTTP rattachés aux domaines d'un environnement, les incidents rattachés aux VPS et projets.

## 2. Décisions de cadrage

| Sujet | Décision |
|---|---|
| Dépôt git | Nouveau dépôt à la racine de `vps monitoring/`, indépendant du dépôt `expertiz` (BackofficeService) |
| Autorisation | 5 rôles fixes, matrice de permissions fixe (migration SQL), vue globale : tout utilisateur connecté voit tous les VPS et projets |
| Modèle projet | Projet → Environnements → VPS ; le « déploiement » est le lien environnement ↔ VPS ; domaines portés par l'environnement ; pas d'historique de versions |
| Structure Core API | Monolithe modulaire, frontières vérifiées par Spring Modulith |
| Contrat d'API | Code-first : springdoc génère l'OpenAPI, exporté dans `contracts/openapi/core-api.yaml`, vérifié en CI ; le frontend en génère ses types |
| UI | Mantine |
| Composants différés | RabbitMQ, Redis, VictoriaMetrics, SMTP : introduits par les phases qui en ont besoin |

## 3. Périmètre

**Inclus :**
- Initialisation du monorepo, Docker Compose (gateway, frontend, core-api, postgres).
- Authentification (login, refresh, logout, changement de mot de passe), création du premier administrateur.
- Gestion des utilisateurs et attribution des rôles.
- CRUD des VPS avec tags.
- CRUD des projets, environnements, domaines, déploiements.
- Journal d'audit et sa consultation.
- Tableau de bord d'inventaire simple.
- Tests (unitaires, intégration, sécurité, E2E) et workflow CI.

**Exclus (phases ultérieures) :** agents et leurs credentials, ingestion, métriques, services surveillés, checks, incidents, alertes, emails (dont le « mot de passe oublié » en libre-service), `server_groups`, historique des déploiements, haute disponibilité.

## 4. Stack et versions

Versions relevées le 2026-11-02, à figer dans le plan d'implémentation :

| Composant | Version |
|---|---|
| Java | 25 (LTS). Le code cible `--release 25` ; le JDK 27 local convient pour compiler |
| Spring Boot | 4.1.x (4.1.2 au moment de la rédaction) |
| Build backend | Wrapper Maven (`mvnw`), aucune installation requise |
| PostgreSQL | 18 (nécessaire pour `uuidv7()`) |
| Migrations | Flyway |
| Node.js | 24 |
| Frontend | Vite 8, React 19, TypeScript (version du template Vite), Mantine 9, TanStack Query 5, React Router 8 |
| Client API | `openapi-typescript` + `openapi-fetch` |
| Tests backend | JUnit 5, Testcontainers (PostgreSQL), Spring Modulith test |
| Tests frontend | Vitest, React Testing Library, MSW |
| E2E | Playwright |
| Gateway | Nginx (image officielle, stable) |

**Conventions :**
- Package Java racine : `com.expertiz.sentinel.core`.
- Identifiants de code en anglais, interface utilisateur en français.
- Commits au format Conventional Commits (`feat:`, `fix:`…).

## 5. Structure du dépôt

```text
vps monitoring/                    (racine du dépôt)
├── frontend/
├── services/
│   └── core-api/
├── contracts/
│   └── openapi/core-api.yaml
├── infrastructure/
│   ├── gateway/                   nginx.conf (TLS), nginx.dev.conf (HTTP local)
│   └── postgres/                  scripts d'initialisation éventuels
├── tests/
│   └── e2e/                       Playwright
├── docs/
│   ├── architecture/              TAD
│   └── superpowers/specs/         specs de conception
├── scripts/                       export OpenAPI, génération de clés JWT de dev
├── .github/workflows/ci.yml
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
└── README.md
```

Les dossiers `monitoring-service/`, `intelligence-service/`, `agent/`, `contracts/events/` et l'infrastructure RabbitMQ, Redis et VictoriaMetrics du TAD §12 seront créés par leurs phases respectives. On ne crée pas de dossiers vides.

## 6. Infrastructure et déploiement

### 6.1 Services Docker Compose

| Service | Image | Réseaux | Rôle |
|---|---|---|---|
| `gateway` | nginx | public, app | Seul service exposé (80/443). Terminaison TLS, routage, en-têtes de sécurité, limitation de débit |
| `frontend` | nginx (build statique multi-stage) | app | Sert le build Vite |
| `core-api` | eclipse-temurin:25-jre (build multi-stage avec `-jdk`) | app, data | API REST |
| `postgres` | postgres:18 | data | Base métier, volume persistant `pgdata` |

- `core-api` dépend de `postgres` avec `condition: service_healthy` (`pg_isready`).
- `core-api` expose un healthcheck sur `/actuator/health`.

### 6.2 Réseaux (TAD §9.3)

- `public` : `gateway` uniquement.
- `app` : `gateway`, `frontend`, `core-api`.
- `data` : `core-api`, `postgres`. PostgreSQL n'est jamais publié par `docker-compose.yml`.

### 6.3 Fichiers Compose

- `docker-compose.yml` : configuration de type production. Le gateway écoute sur 80, qui redirige vers 443, et sur 443 avec des certificats montés depuis `${SENTINEL_TLS_CERT_DIR}` (Let's Encrypt sur l'hôte).
- `docker-compose.dev.yml` (surcharge) :
  - le gateway utilise `nginx.dev.conf` en HTTP sur `localhost:8088` ;
  - PostgreSQL est publié sur `localhost:5432` ;
  - les clés JWT de dev sont montées.

### 6.4 Gateway Nginx

- Routes :
  - `/api/` → `core-api:8080` ;
  - `/actuator/health` → `core-api:8080/actuator/health` (aucun autre endpoint Actuator n'est routé) ;
  - `/` → `frontend`.
- `proxy_set_header X-Request-Id $request_id` : l'identifiant est toujours généré par le gateway, et une valeur envoyée par le client est écrasée.
- Limitation de débit sur `POST /api/v1/auth/login` : 10 requêtes par minute et par IP, rafale de 5, réponse 429 au-delà.
- En-têtes de sécurité :
  - `Strict-Transport-Security` (configuration TLS uniquement) ;
  - `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'` ;
  - `X-Content-Type-Options: nosniff` ;
  - `Referrer-Policy: strict-origin-when-cross-origin`.
- `client_max_body_size 1m`.

### 6.5 Configuration (`.env`, non commité ; `.env.example` commité)

| Variable | Rôle |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base de données |
| `SENTINEL_JWT_PRIVATE_KEY_PATH`, `SENTINEL_JWT_PUBLIC_KEY_PATH` | Paire RSA (PEM) montée dans le conteneur |
| `SENTINEL_ALLOWED_ORIGINS` | Liste d'origines séparées par des virgules, contrôlées sur `/auth/refresh` et `/auth/logout`. Prod : l'origine publique (ex. `https://sentinel.example.com`). Dev : `http://localhost:5173,http://localhost:8088` |
| `SENTINEL_REFRESH_COOKIE_SECURE` | `true` par défaut |
| `SENTINEL_BOOTSTRAP_ADMIN_EMAIL`, `SENTINEL_BOOTSTRAP_ADMIN_PASSWORD`, `SENTINEL_BOOTSTRAP_ADMIN_NAME` | Création du premier SUPER_ADMIN (§8.6) |
| `SENTINEL_TLS_CERT_DIR` | Répertoire des certificats du gateway (prod) |

- **Profil `prod`** : l'application refuse de démarrer si les clés JWT sont absentes.
- **Profil `dev`** : si aucune clé n'est configurée, une paire éphémère est générée au démarrage. `scripts/` fournit aussi un générateur de paire persistante pour le dev Docker.

### 6.6 Workflow de développement

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres   # base seule
cd services/core-api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # API sur :8080
cd frontend && npm run dev                                                      # UI sur :5173, proxy /api -> :8080
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build       # pile complète sur :8088
```

## 7. Core API — architecture modulaire

### 7.1 Modules

| Module | Package | Responsabilités | Dépend de |
|---|---|---|---|
| `shared` (ouvert) | `…core.shared` | Problem Details, pagination, filtre de corrélation, constantes de permissions (`Permissions`), type `AuditEvent` | — |
| `identity` | `…core.identity` | Utilisateurs, rôles, permissions, authentification, jetons, configuration Spring Security, création du premier admin | shared |
| `infrastructure` | `…core.infrastructure` | VPS, tags, facettes | shared |
| `projects` | `…core.projects` | Projets, environnements, domaines, déploiements | shared, infrastructure |
| `audit` | `…core.audit` | Écriture et consultation du journal d'audit | shared |

Spring Modulith (`ApplicationModules.verify()`) interdit l'accès aux packages internes d'un autre module et les dépendances cycliques. `shared` est déclaré `OPEN`.

### 7.2 Interactions entre modules

- **`projects` → `infrastructure`** : `projects` utilise l'API publique `ServerCatalog` (existence, statut, résumé d'un VPS) pour valider la création d'un déploiement.
- **Suppression d'un VPS sans dépendance cyclique** : `infrastructure` définit une interface publique `ServerUsageChecker`, que `projects` implémente. `infrastructure` la consulte avant de supprimer un VPS, sans dépendre de `projects`.
- **Concurrence** :
  - la création d'un déploiement verrouille la ligne du VPS en lecture partagée (`FOR SHARE`) ;
  - la suppression d'un VPS la verrouille en écriture (`FOR UPDATE`).
  Ce couple élimine la course entre « rattacher » et « supprimer ».
- **Audit** :
  - chaque module publie un `AuditEvent(action, targetType, targetId, details)`, défini dans `shared`, via `ApplicationEventPublisher` ;
  - `audit` l'écoute de façon **synchrone, dans la même transaction**. Aucune modification n'est donc validée sans sa ligne d'audit ;
  - par défaut, l'acteur (claims `sub` et `email` du JWT), l'IP et le `correlationId` sont résolus par `audit` à partir du contexte de sécurité, de la requête et du MDC ;
  - un `AuditEvent` peut aussi porter un acteur explicite. C'est le cas du login, où le contexte de sécurité est encore vide (acteur `ANONYMOUS`, ou l'utilisateur identifié par son email), et des actions `SYSTEM` comme le bootstrap.
- **Endpoint transverse** : la liste des environnements hébergés par un VPS est servie par `projects` (`GET /servers/{id}/deployments`). Le chemin d'URL n'impose pas le module propriétaire.

## 8. Authentification et sécurité

### 8.1 Access token

- JWT signé en **RS256**, valable **15 minutes**.
- Claims : `iss = vps-sentinel`, `sub` (id utilisateur), `email`, `iat`, `exp`, `jti`, `role`, `perms` (liste des permissions du rôle), `pcr` (booléen « changement de mot de passe requis »).
- Les autorités Spring Security sont construites depuis `perms`. Le jeton est autoportant : un futur Monitoring Service pourra le valider avec la seule clé publique, sans base de données.
- Validation via `spring-boot-starter-oauth2-resource-server` (décodeur Nimbus, clé publique RSA).
- Autorisation par `@PreAuthorize("hasAuthority('server:write')")`, avec les constantes de `Permissions`.
- Si `pcr = true`, tout endpoint autre que `POST /auth/change-password`, `POST /auth/logout` et `GET /me` répond **403** avec le type de problème `password-change-required`.

### 8.2 Refresh token

- Valeur opaque aléatoire de 256 bits, encodée en base64url. Seul son hash SHA-256 est stocké.
- Cookie `sentinel_rt` : `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- Durée de vie : **7 jours** par jeton, avec rotation à chaque utilisation.
- Durée **absolue** de session : **30 jours** à partir de `session_started_at`. Au-delà, une reconnexion est requise.
- `POST /auth/refresh` et `POST /auth/logout` exigent un en-tête `Origin` présent dans `SENTINEL_ALLOWED_ORIGINS`. Sinon, réponse 403.

**Algorithme de `POST /auth/refresh` :**
1. Cookie absent ou hash inconnu : 401.
2. Jeton révoqué :
   - si `revoked_reason = ROTATED`, c'est une **réutilisation** : toute la famille (`family_id`) est révoquée avec `REUSE_DETECTED`, un audit `TOKEN_REUSE_DETECTED` est écrit, et la réponse est 401 ;
   - sinon, 401.
3. Jeton expiré ou session de plus de 30 jours : 401.
4. Utilisateur non `ACTIVE` ou supprimé : la famille est révoquée, réponse 401.
5. Sinon, rotation :
   - l'ancien jeton passe à `revoked_reason = ROTATED`, avec `replaced_by` renseigné ;
   - un nouveau jeton est créé dans la même famille, avec le même `session_started_at` ;
   - un nouvel access token est émis ;
   - réponse 200, avec le même corps que le login.

**Plusieurs onglets :** le frontend sérialise le refresh entre onglets avec la Web Locks API (`navigator.locks`). Le cookie étant partagé, l'onglet suivant envoie le jeton déjà renouvelé, ce qui évite les faux positifs de réutilisation.

### 8.3 Login

`POST /auth/login {email, password}` :
1. Recherche de l'utilisateur non supprimé par `lower(email)`.
2. Utilisateur introuvable : une vérification Argon2 factice est exécutée pour égaliser le temps de réponse. Audit `LOGIN_FAILED` (acteur `ANONYMOUS`, `details.emailAttempted`). Réponse 401.
3. `DISABLED`, ou `locked_until > now()` : audit `LOGIN_FAILED` (`details.reason`). Réponse 401.
4. Mot de passe faux : `failed_login_count + 1`. Au 5ᵉ échec :
   - `locked_until = now() + 15 min` ;
   - le compteur repasse à 0 ;
   - audit `ACCOUNT_LOCKED`.
   Sinon, audit `LOGIN_FAILED`. Réponse 401.
5. Succès :
   - le compteur repasse à 0 et `last_login_at` est mis à jour ;
   - une nouvelle famille de refresh token est créée, avec `session_started_at = now()` ;
   - audit `LOGIN_SUCCEEDED`.
   Réponse 200 :

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

Toutes les réponses 401 du login portent le même message : **« Identifiants invalides ou compte temporairement verrouillé. »**

### 8.4 Logout et changement de mot de passe

- `POST /auth/logout` : révoque toute la famille du cookie (`LOGOUT`) et efface le cookie. Fonctionne même avec un access token expiré. Réponse 204.
- `POST /auth/change-password {currentPassword, newPassword}`, authentifié :
  - vérifie le mot de passe actuel et la politique (§8.5) ;
  - le nouveau doit différer de l'actuel ;
  - révoque **tous** les refresh tokens de l'utilisateur (`PASSWORD_CHANGED`) ;
  - passe `must_change_password` à `false` ;
  - émet une nouvelle paire de jetons, avec le même corps de réponse que le login ;
  - audit `PASSWORD_CHANGED`.

### 8.5 Mots de passe

- Hachage **Argon2id** (`Argon2PasswordEncoder`, BouncyCastle), via `DelegatingPasswordEncoder` avec `argon2` par défaut.
- Politique :
  - 12 caractères minimum, 128 maximum ;
  - différent de l'email (comparaison insensible à la casse) ;
  - pas d'autre règle de composition imposée.
- Les mots de passe temporaires sont générés par le serveur (16 caractères aléatoires). Ils sont renvoyés **une seule fois** dans la réponse et ne sont jamais journalisés.

### 8.6 Premier administrateur

Au démarrage, une seule fois, si les deux conditions sont réunies :
- aucun utilisateur actif ne porte le rôle `SUPER_ADMIN` ;
- `SENTINEL_BOOTSTRAP_ADMIN_EMAIL` et `SENTINEL_BOOTSTRAP_ADMIN_PASSWORD` sont définis.

L'application crée alors ce compte avec `must_change_password = true`, et écrit un audit `USER_CREATED` avec l'acteur `SYSTEM`.

- Si un SUPER_ADMIN actif existe déjà, les variables sont ignorées, sans erreur.
- Si l'email de bootstrap appartient déjà à un utilisateur non supprimé qui n'est pas SUPER_ADMIN, l'application journalise une erreur explicite et ne fait rien : aucune promotion automatique. Le démarrage n'est pas bloqué.

### 8.7 Garde-fous de gestion des utilisateurs

- Impossible de désactiver, supprimer ou retirer le rôle `SUPER_ADMIN` du **dernier** SUPER_ADMIN actif : réponse 409, type `last-super-admin`.
- Un utilisateur ne peut ni modifier son propre rôle, ni se désactiver, ni se supprimer : réponse 409, type `self-modification-forbidden`.
- Désactiver, supprimer ou changer le rôle d'un utilisateur révoque tous ses refresh tokens (`USER_DISABLED` ou `ROLE_CHANGED`). Son access token en cours reste valable au plus 15 minutes. C'est un compromis assumé : le JWT n'est pas le seul mécanisme de révocation (TAD §8.1).

### 8.8 Matrice des permissions

Fixe, insérée par migration Flyway, sans écran de modification :

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

- `project:write` couvre la création et la modification des projets et environnements, ainsi que l'ajout et le retrait de domaines et de déploiements.
- `project:delete` couvre la suppression d'un projet ou d'un environnement.

## 9. Modèle de données

### 9.1 Conventions

- Clés primaires `uuid DEFAULT uuidv7()`.
- `created_at`, `updated_at` en `timestamptz NOT NULL DEFAULT now()`, en UTC.
- `version bigint NOT NULL DEFAULT 0` (verrouillage optimiste JPA `@Version`) sur `users`, `servers`, `projects`, `environments`.
- Suppression logique `deleted_at timestamptz` sur `users`, `servers`, `projects`, `environments`. Les unicités fonctionnelles passent par des index uniques partiels `WHERE deleted_at IS NULL`.
- Les colonnes d'énumération sont des `varchar` avec contrainte `CHECK`.
- Hibernate en mode `ddl-auto=validate` : seul Flyway modifie le schéma.

### 9.2 Identity

**`users`**
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| email | varchar(254) | NOT NULL ; unique sur `lower(email)` parmi les non supprimés |
| full_name | varchar(120) | NOT NULL |
| password_hash | varchar(255) | NOT NULL |
| status | varchar(16) | NOT NULL, `ACTIVE` \| `DISABLED` |
| must_change_password | boolean | NOT NULL DEFAULT true |
| failed_login_count | int | NOT NULL DEFAULT 0 |
| locked_until | timestamptz | NULL |
| last_login_at | timestamptz | NULL |
| created_at, updated_at, deleted_at, version | | voir §9.1 |

**`roles`** : `id`, `code varchar(32) UNIQUE`, `label varchar(64)`. Les 5 lignes sont insérées par migration.

**`permissions`** : `id`, `code varchar(64) UNIQUE`, `description varchar(255)`. Les 9 lignes sont insérées par migration.

**`role_permissions`** : `role_id`, `permission_id`, PK composite. La matrice du §8.8 est insérée par migration.

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
| expires_at | timestamptz | NOT NULL |
| revoked_at | timestamptz | NULL |
| revoked_reason | varchar(32) | NULL : `ROTATED` \| `LOGOUT` \| `REUSE_DETECTED` \| `USER_DISABLED` \| `ROLE_CHANGED` \| `PASSWORD_CHANGED` \| `PASSWORD_RESET` |
| replaced_by | uuid | NULL |
| user_agent | varchar(255) | NULL |
| ip | inet | NULL |

Une tâche planifiée quotidienne supprime les jetons expirés depuis plus de 30 jours.

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
| cpu_cores | smallint | NULL, > 0 |
| ram_mb | int | NULL, > 0 |
| disk_gb | int | NULL, > 0 |
| admin_status | varchar(16) | NOT NULL DEFAULT `ACTIVE`, `ACTIVE` \| `MAINTENANCE` \| `DECOMMISSIONED` |
| description | varchar(2000) | NULL |
| created_at, updated_at, deleted_at, version | | voir §9.1 |

**`server_tags`** : `server_id` (FK servers, `ON DELETE CASCADE`), `tag varchar(50)`, PK `(server_id, tag)`. Les tags sont normalisés en minuscules et doivent respecter `^[a-z0-9][a-z0-9._-]*$`. Ils sont gérés par remplacement complet de la liste dans `PATCH /servers/{id}`.

### 9.4 Projets

**`projects`** : `id`, `name varchar(100)` (unique sur `lower(name)` parmi les non supprimés), `description varchar(2000)`, `repository_url varchar(500)` (URL http(s) valide), plus les colonnes du §9.1.

**`environments`** : `id`, `project_id` (FK projects, NOT NULL), `name varchar(50)` (unique sur `(project_id, lower(name))` parmi les non supprimés), `kind varchar(16)` (`PRODUCTION` \| `STAGING` \| `DEVELOPMENT` \| `OTHER`), plus les colonnes du §9.1.

**`domains`**
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| environment_id | uuid | FK environments, NOT NULL |
| hostname | varchar(253) | NOT NULL ; unique global sur `lower(hostname)` ; FQDN sans schéma, port ni chemin |
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

Un déploiement ne peut pas viser un VPS supprimé ou `DECOMMISSIONED` : réponse 409, type `server-not-deployable`.

### 9.5 Audit

**`audit_logs`** (en ajout seul)
| Colonne | Type | Contraintes |
|---|---|---|
| id | uuid | PK |
| occurred_at | timestamptz | NOT NULL DEFAULT now(), indexé (desc) |
| actor_type | varchar(16) | NOT NULL, `USER` \| `SYSTEM` \| `ANONYMOUS` |
| actor_user_id | uuid | NULL, indexé (pas de FK : le journal reste indépendant) |
| actor_email | varchar(254) | NULL (copie figée) |
| action | varchar(64) | NOT NULL, indexé |
| target_type | varchar(32) | NULL |
| target_id | uuid | NULL ; index `(target_type, target_id)` |
| details | jsonb | NOT NULL DEFAULT `'{}'` |
| ip | inet | NULL |
| correlation_id | varchar(64) | NULL |

- Un trigger PostgreSQL rejette tout `UPDATE` et tout `DELETE` sur `audit_logs`.
- Pour une modification, `details` contient `{"changes": {"<champ>": {"from": …, "to": …}}}`. Les mots de passe, hashes et jetons n'y figurent jamais.

**Actions auditées :**
- `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `LOGOUT`, `TOKEN_REUSE_DETECTED`, `PASSWORD_CHANGED`
- `USER_CREATED`, `USER_UPDATED`, `USER_ROLE_CHANGED`, `USER_DISABLED`, `USER_ENABLED`, `USER_DELETED`, `USER_PASSWORD_RESET`
- `SERVER_CREATED`, `SERVER_UPDATED`, `SERVER_DELETED`
- `PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_DELETED`
- `ENVIRONMENT_CREATED`, `ENVIRONMENT_UPDATED`, `ENVIRONMENT_DELETED`
- `DOMAIN_ADDED`, `DOMAIN_REMOVED`, `DEPLOYMENT_ADDED`, `DEPLOYMENT_REMOVED`

### 9.6 Règles de suppression

| Entité | Effet |
|---|---|
| Utilisateur | Suppression logique, statut `DISABLED`, révocation des jetons. Garde-fous du §8.7 |
| VPS | Refusée (409, type `server-in-use`) tant qu'il existe des déploiements ; sinon suppression logique. Ses tags sont conservés |
| Projet | Dans une même transaction : suppression logique du projet et de ses environnements, suppression physique de leurs domaines et déploiements, un audit par élément supprimé |
| Environnement | Suppression logique ; suppression physique de ses domaines et déploiements ; audits correspondants |
| Domaine, déploiement | Suppression physique, auditée |

## 10. API REST

Préfixe `/api/v1`. Format JSON. L'OpenAPI est exposé par springdoc sur `/v3/api-docs` (non routé par le gateway en prod) et exporté dans `contracts/openapi/core-api.yaml`.

### 10.1 Endpoints

| Méthode et chemin | Permission | Remarques |
|---|---|---|
| `POST /auth/login` | public | §8.3 |
| `POST /auth/refresh` | cookie | §8.2 |
| `POST /auth/logout` | cookie | §8.4 |
| `POST /auth/change-password` | authentifié | §8.4 |
| `GET /me` | authentifié | Profil et permissions |
| `GET /users` | `user:read` | Filtres `q` (nom ou email), `role`, `status` |
| `POST /users` | `user:manage` | `{email, fullName, role}` → 201 avec `temporaryPassword` (affiché une seule fois) |
| `GET /users/{id}` | `user:read` | |
| `PATCH /users/{id}` | `user:manage` | `{fullName?, role?, status?, version}` |
| `DELETE /users/{id}` | `user:manage` | 204 |
| `POST /users/{id}/reset-password` | `user:manage` | Nouveau mot de passe temporaire, `must_change_password = true`, révocation des jetons (`PASSWORD_RESET`) |
| `GET /servers` | `server:read` | Filtres `q` (nom, hostname, IP), `tag`, `provider`, `region`, `adminStatus` |
| `POST /servers` | `server:write` | 201 |
| `GET /servers/{id}` | `server:read` | |
| `PATCH /servers/{id}` | `server:write` | Champs partiels avec `version` ; `tags` remplace la liste complète |
| `DELETE /servers/{id}` | `server:delete` | 204, ou 409 `server-in-use` |
| `GET /servers/facets` | `server:read` | `{providers[], regions[], tags[]}` (valeurs distinctes existantes) |
| `GET /servers/{id}/deployments` | `project:read` | Environnements hébergés, avec le projet associé |
| `GET /projects` | `project:read` | Filtre `q` ; chaque élément inclut le nombre d'environnements |
| `POST /projects` | `project:write` | 201 |
| `GET /projects/{id}` | `project:read` | Inclut les environnements, leurs domaines et leurs déploiements (avec nom et statut du VPS) |
| `PATCH /projects/{id}` | `project:write` | |
| `DELETE /projects/{id}` | `project:delete` | 204 |
| `POST /projects/{id}/environments` | `project:write` | 201 |
| `PATCH /environments/{id}` | `project:write` | |
| `DELETE /environments/{id}` | `project:delete` | 204 |
| `POST /environments/{id}/domains` | `project:write` | `{hostname, isPrimary}` → 201. Si `isPrimary`, l'ancien domaine principal perd ce statut |
| `DELETE /domains/{id}` | `project:write` | 204 |
| `POST /environments/{id}/deployments` | `project:write` | `{serverId, role?}` → 201, ou 409 `server-not-deployable` / `deployment-exists` |
| `DELETE /deployments/{id}` | `project:write` | 204 |
| `GET /audit-logs` | `audit:read` | Filtres `actorUserId`, `action`, `targetType`, `targetId`, `from`, `to` ; tri par date décroissante |

### 10.2 Conventions

- **Pagination** des listes :
  - paramètres `?page=0&size=20&sort=name,asc`, avec `size` limité à 100 ;
  - réponse `{"items": [...], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3}`.
- **Verrouillage optimiste** : les `PATCH` portent la `version` lue. Si elle ne correspond plus, réponse 409, type `version-conflict`.
- **Validation** : Bean Validation sur des DTO distincts des entités JPA.
- **Dates** en ISO-8601 UTC (`2026-11-02T10:30:00Z`).

## 11. Erreurs, logs et traçabilité

- **Problem Details (RFC 9457)**, `Content-Type: application/problem+json`, via `ProblemDetail` de Spring et un `@RestControllerAdvice` dans `shared` :

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

- **Types de problèmes** (le champ `type` vaut `urn:sentinel:problem:<code>`) : `validation-error` (400), `unauthorized` (401), `forbidden` (403), `password-change-required` (403), `not-found` (404), `version-conflict` (409), `last-super-admin` (409), `self-modification-forbidden` (409), `server-in-use` (409), `server-not-deployable` (409), `deployment-exists` (409), `duplicate` (409, violation d'unicité), `rate-limited` (429, gateway), `internal-error` (500, sans détail technique).
- **Corrélation** :
  - un filtre lit `X-Request-Id`, ou en génère un s'il est absent (dev sans gateway) ;
  - il le place dans le MDC sous `correlationId` ;
  - il le renvoie dans l'en-tête de réponse et dans le corps des erreurs ;
  - `audit` l'enregistre.
- **Logs** : JSON structuré (journalisation structurée native de Spring Boot) en profil `prod`, texte lisible en `dev`. Aucun mot de passe, jeton ou hash n'est journalisé.

## 12. Frontend

### 12.1 Organisation

```text
frontend/src/
├── api/            schema.d.ts (généré), client openapi-fetch, middleware d'auth
├── auth/           AuthProvider, garde-fous de routes, gestion du refresh
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

- L'access token et l'utilisateur courant sont gardés **en mémoire** dans `AuthProvider`, jamais dans localStorage.
- Au chargement de l'application, un appel à `POST /auth/refresh` est fait : en cas de succès, la session est restaurée ; sinon, redirection vers `/login`.
- Middleware `openapi-fetch` :
  - il ajoute `Authorization: Bearer` ;
  - sur une réponse 401, il lance un seul refresh (partagé entre requêtes, sérialisé entre onglets via `navigator.locks`), puis rejoue la requête une fois ;
  - si le refresh échoue, la session est vidée et l'utilisateur est redirigé vers `/login`.
- Si `mustChangePassword`, ou sur une réponse 403 `password-change-required`, redirection forcée vers `/change-password`.

### 12.3 Pages

| Route | Accès | Contenu |
|---|---|---|
| `/login` | public | Formulaire email et mot de passe |
| `/change-password` | authentifié | Changement forcé ou volontaire |
| `/` | authentifié | Inventaire : nombre de VPS par statut administratif, nombre de projets et, pour les rôles qui ont `audit:read`, les 10 dernières actions du journal. Assemblé à partir des endpoints de liste (`totalItems`), sans endpoint dédié |
| `/servers` | `server:read` | Tableau paginé, filtres synchronisés avec l'URL |
| `/servers/new`, `/servers/:id/edit` | `server:write` | Formulaire VPS ; tags, fournisseur et région auto-complétés via `/servers/facets` |
| `/servers/:id` | `server:read` | Fiche VPS et environnements hébergés |
| `/projects` | `project:read` | Liste paginée |
| `/projects/:id` | `project:read` | Fiche projet, un onglet par environnement (domaines, déploiements) ; ajouts dans des modales |
| `/users` | `user:read` | Liste ; création, modification et réinitialisation si `user:manage`. Mot de passe temporaire affiché une fois, avec bouton de copie |
| `/audit` | `audit:read` | Journal filtrable |
| `/profile` | authentifié | Profil, lien vers le changement de mot de passe |

### 12.4 Comportements transverses

- Mise en page avec `AppShell` de Mantine. Navigation : Tableau de bord, VPS, Projets, Utilisateurs\*, Audit\* (\* masqués sans la permission). Menu utilisateur (profil, déconnexion) et bascule clair/sombre.
- Les routes et les actions sont masquées selon `permissions`. C'est du confort d'interface : le backend reste l'autorité.
- Erreurs :
  - les `errors[]` des Problem Details sont mappés sur les champs du formulaire (`@mantine/form`) ;
  - les autres erreurs s'affichent en notification ;
  - un 409 `version-conflict` affiche « Cet élément a été modifié par quelqu'un d'autre. Rechargez la page. »
- Toute suppression demande une confirmation.
- Les types d'API sont générés par `npm run gen:api` (`openapi-typescript ../contracts/openapi/core-api.yaml`). Le fichier généré est commité.

## 13. Stratégie de tests

### 13.1 Backend (`./mvnw verify`)

- **Unitaires :**
  - règles de verrouillage du compte ;
  - rotation des refresh tokens, détection de réutilisation, expiration absolue ;
  - garde-fous du §8.7 ;
  - politique de mot de passe ;
  - normalisation des tags et des domaines ;
  - calcul des diffs d'audit.
- **Intégration** : `@SpringBootTest` avec Testcontainers PostgreSQL 18, migrations Flyway réelles. Chaque endpoint est testé : succès, validation, 404, 409.
- **Matrice de sécurité** : un test paramétré pilote, pour chaque endpoint du §10.1 et chaque rôle, le statut attendu (succès ou 403), à partir de la matrice du §8.8. Plus des tests 401 sans jeton et 403 `password-change-required`.
- **Audit** : vérification qu'une mutation écrit sa ligne d'audit dans la même transaction (rollback, donc pas d'audit), et que le trigger rejette `UPDATE` et `DELETE`.
- **Modulith** : `ApplicationModules.of(…).verify()`.
- **Contrat** : un test récupère `/v3/api-docs.yaml` et le compare à `contracts/openapi/core-api.yaml`. Il échoue en cas de différence. `scripts/export-openapi.sh` régénère le fichier.

### 13.2 Frontend (`npm run lint && npm run typecheck && npm test && npm run build`)

- Vitest + React Testing Library + MSW :
  - middleware d'auth (401, refresh unique, nouvel essai, échec qui redirige) ;
  - garde-fous de routes ;
  - formulaires VPS et projet (validation, mapping des erreurs serveur) ;
  - affichage unique du mot de passe temporaire.
- Vérification que `schema.d.ts` est à jour : `npm run gen:api && git diff --exit-code`.

### 13.3 E2E (`tests/e2e`, Playwright)

Scénario de validation, exécuté sur `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build` (`http://localhost:8088`), avec une base vierge :
1. Connexion avec l'admin initial (variables de bootstrap).
2. Changement de mot de passe forcé.
3. Création du VPS `VPS-01` (fournisseur, région, OS, tags).
4. Création du projet `Demo` avec un environnement `prod` (kind `PRODUCTION`) et le domaine `demo.example.com`.
5. Rattachement de l'environnement `prod` à `VPS-01` (rôle « web »).
6. Vérifications :
   - la fiche `VPS-01` liste `Demo / prod` ;
   - le journal d'audit contient les actions correspondantes.
7. Tentative de suppression de `VPS-01` : refusée avec le message « VPS utilisé par des déploiements ».

### 13.4 CI (`.github/workflows/ci.yml`)

Trois jobs, sur push et sur pull request :
- **backend** : `setup-java` 25, `./mvnw verify` (Docker est disponible sur `ubuntu-latest` pour Testcontainers) ;
- **frontend** : `npm ci`, lint, typecheck, tests, build, vérification de `gen:api` ;
- **e2e** : build et démarrage de la pile Compose dev, puis `npx playwright test`. Il ne s'exécute qu'après la réussite des deux autres.

Le workflow est commité, mais ne s'exécutera qu'une fois le dépôt poussé sur GitHub.

## 14. Critères d'acceptation

1. Depuis un clone propre, `cp .env.example .env` puis `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build` démarrent les 4 services. Tous les healthchecks sont verts.
2. Le premier SUPER_ADMIN est créé depuis les variables de bootstrap, et le changement de mot de passe est forcé à la première connexion.
3. Le scénario E2E du §13.3 passe.
4. `./mvnw verify` est vert, ce qui inclut la matrice de sécurité, Modulith et le contrat OpenAPI. Les vérifications frontend du §13.2 sont vertes.
5. Chaque mutation et chaque événement d'authentification du §9.5 produit une ligne d'audit, avec l'acteur et le `correlationId`.
6. Avec `docker-compose.yml` seul, PostgreSQL et core-api ne sont pas joignables depuis l'hôte : seul le gateway publie des ports.

## 15. Points ouverts pour les phases suivantes

Relevés dans le TAD, hors périmètre de la phase 1. Ils sont listés pour ne pas être perdus :

- **Phase 2 et ingestion** :
  - le critère de la phase 2 (« un VPS transmet ses données au Control Plane ») suppose un endpoint d'ingestion, que la roadmap place en phase 3 ;
  - il faudra soit livrer un endpoint d'ingestion minimal en phase 2, soit fusionner le critère.
- **Enrôlement des agents** : le mécanisme de première identification (jeton d'enrôlement à usage unique, généré depuis la fiche VPS ?) est à concevoir en phase 2.
- **RabbitMQ** : le Monitoring Service publie des événements (TAD §3.4), mais RabbitMQ arrive en phase 4. Il faut décider si la phase 3 fonctionne en mémoire, ou avancer RabbitMQ.
- **Sondes externes (TAD §6.3) et watchdog indépendant (TAD §9.5)** : aucune phase de la roadmap ne les porte.
- **Distribution de la clé publique JWT** au Monitoring Service (fichier partagé ou endpoint JWKS) : phase 3.
- **Sauvegardes PostgreSQL (TAD §9.4)** : non couvertes par la phase 1. À traiter avant tout déploiement en production.
- **Mot de passe oublié en libre-service** : phase 5, avec SMTP.
