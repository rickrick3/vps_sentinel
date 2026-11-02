# VPS Sentinel — Phase 1 « Foundation » — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** livrer le socle décrit par la spec v2 : dépôt et hygiène, infrastructure Docker, Core API sécurisée (identité, inventaire, audit), frontend Mantine, tests et CI.

**Architecture :**

- Monolithe modulaire Spring Boot (`shared`, `identity`, `audit`, `infrastructure`, `projects`), dont les frontières sont vérifiées par Spring Modulith, derrière un gateway Nginx.
- PostgreSQL avec rôles séparés ; migrations Flyway exécutées par un conteneur ponctuel.
- SPA React/Mantine qui consomme le contrat OpenAPI généré.
- Les tâches suivent l'ordre des dépendances : hygiène → base → noyau partagé → audit → identité → inventaire → API transverse → frontend → conteneurs → E2E et CI.

**Tech Stack :** Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.1, Hibernate 7, Jackson 3, PostgreSQL 18, Flyway 12.4.0, Testcontainers 2.0.5, React 19, Vite 8, Mantine 9, TanStack Query 5, React Router 8, Nginx 1.30, Playwright.

**Spec :** [docs/superpowers/specs/2026-11-02-phase1-foundation-design.md](../specs/2026-11-02-phase1-foundation-design.md) (v2). Les renvois « spec §x » pointent vers ce document. Le lire avant toute tâche.

## Global Constraints

**Versions backend :**

- `maven.compiler.release` 25 ; Spring Boot `4.1.2` (parent) ; Spring Modulith `2.1.1` (BOM).
- Versions gérées par Boot : Flyway `12.4.0`, Testcontainers `2.0.5`, Hibernate `7.4.13.Final`, Jackson `3.1.7` (paquets `tools.jackson.*`), JUnit `6.0.3`.
- Versions fixées à la main : springdoc `3.2.0` (`springdoc-openapi-starter-webmvc-api`), `org.bouncycastle:bcprov-jdk18on:1.86`, `com.fasterxml.uuid:java-uuid-generator:5.2.0`.
- Wrapper Maven `3.3.4`, `distributionType=only-script`, Maven `3.10.0` (tel que généré par Spring Initializr).

**Versions frontend :** Node 24 ; TypeScript `5.9.3` ; Vite `8.3.5` ; `@vitejs/plugin-react` `6.2.1` ; React et React DOM `19.3.1` ; `@mantine/*` `9.7.4` ; `@tanstack/react-query` `5.105.2` ; `react-router` `8.5.1` ; `openapi-typescript` `7.13.0` ; `openapi-fetch` `0.17.0`.

**Outillage frontend :** Vitest `5.0.5` ; jsdom `30.1.3` ; MSW `3.0.3` ; `@testing-library/react` `16.3.3`, `user-event` `14.6.8`, `jest-dom` `7.0.1` ; ESLint `9.39.5` ; `typescript-eslint` `8.72.1` ; `eslint-plugin-react` `7.37.5` ; `eslint-plugin-react-hooks` `7.1.1` ; `@playwright/test` `1.64.1`.

**Images Docker :** `postgres:18`, `flyway/flyway:12.4.0-alpine`, `eclipse-temurin:25-jdk`, `eclipse-temurin:25-jre`, `node:24-alpine`, `nginx:1.30.6-alpine`.

**Noms et identifiants :**

- Package racine : `com.expertiz.sentinel.core`.
- API : préfixe `/api/v1`. Type des erreurs : `urn:sentinel:problem:<code>` (spec §11).
- Base : schéma `sentinel`, base `sentinel`, rôles `sentinel_owner` et `sentinel_app` (spec §6.5).
- Secrets : `/run/secrets/jwt-private.pem`, `jwt-public.pem` et `hmac.key` (base64, au moins 32 octets une fois décodé).
- Variables `SENTINEL_*` : exactement celles du spec §6.6, aucune autre. Seule `SPRING_PROFILES_ACTIVE`, variable standard de Spring, s'y ajoute.

**Valeurs fixées par la spec :**

- Access token : 15 min. Refresh token : 24 h d'inactivité, 7 jours au total.
- Cookie : `sentinel_rt`, `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- Mot de passe temporaire : valable 72 h, 16 caractères tirés de l'alphabet `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789` (56 symboles).
- Limitation des tentatives :
  - seuils : 5 échecs pour la portée `ACCOUNT_IP`, 20 pour la portée `IP` ;
  - délai `min(30 s × 2^(n−seuil), 15 min)` ;
  - compteur remis à zéro 1 h après le dernier échec.
- Pagination : `size` entre 1 et 100.

**Temps :** aucune décision métier n'utilise le `now()` SQL. L'instant vient du bean `java.time.Clock` et est passé en paramètre `:now`. `DEFAULT now()` reste seulement un filet. C'est ce qui rend les tests d'expiration contrôlables ; cela précise le bloc SQL du spec §8.2.

**Interface :** textes en français, identifiants de code en anglais.

**Git :**

- Toute commande git s'exécute depuis la racine du dépôt VPS Sentinel. Jamais `git add .` ni `git add -A` depuis `expertiz`.
- Fins de ligne LF (§5.3 de la spec).
- Commits au format Conventional Commits.

**Commandes de référence** (les variables pointent vers le JDK 25 installé à la tâche 0) :

- backend : `cd services/core-api && ./mvnw -q test -Dtest=<Classe>` ; tout : `./mvnw verify` ;
- frontend : `cd frontend && npx vitest run <fichier>` ; tout : `npm run lint && npm run typecheck && npm test && npm run build`.

## Review Focus

Cas d'usage réels qu'aucun test ne couvrait au départ. Chacun a reçu son test dans la tâche indiquée.

1. **Nom en double à la casse ou aux espaces près :** créer « VPS-01 » puis «  vps-01  ». On attend un 409 `duplicate` sur le champ `name`, pas un 500 ni un doublon (tâche 14).
2. **Double soumission d'un formulaire :** le bouton est désactivé pendant l'envoi, ce qui évite deux créations (tâche 20).
3. **Modification d'un enfant d'un parent supprimé :** un `PUT` sur un environnement dont le projet a été supprimé renvoie 404 `not-found`, pas 500 (tâche 15).
4. **Client en IPv6** (`2001:db8::1` via `X-Forwarded-For`) : l'adresse est stockée telle quelle dans l'audit et la limitation fonctionne (tâche 10).
5. **Email de bootstrap avec majuscules et espaces** («  Admin@Sentinel.LOCAL ») : il est normalisé, et la connexion en minuscules fonctionne (tâche 12).

---

## Structure des fichiers

```text
<racine>/                                   (vps-sentinel/ après la tâche 0)
├── .gitattributes .gitignore .editorconfig .env.example README.md
├── docker-compose.yml docker-compose.dev.yml docker-compose.prod.yml
├── scripts/
│   ├── check-repo.sh                       fins de ligne, bit exécutable, fichiers d'hygiène (§13.9)
│   ├── gen-dev-secrets.sh                  clés JWT RSA 3072, clé HMAC (base64) dans infrastructure/secrets/dev/
│   ├── check-compose-ports.sh              ports publiés dev et prod (§6.3)
│   ├── export-openapi.sh                   régénère contracts/openapi/core-api.yaml
│   └── e2e.sh                              pile dev vierge, puis Playwright
├── contracts/openapi/core-api.yaml
├── infrastructure/
│   ├── postgres/init/01-roles.sh
│   ├── gateway/{nginx.dev.conf,nginx.prod.conf,security-headers.conf,proxy-headers.conf}
│   └── secrets/                            ignoré par git
├── docs/deployment/production-prerequisites.md
├── .github/workflows/ci.yml
├── services/core-api/
│   ├── pom.xml mvnw mvnw.cmd .mvn/wrapper/maven-wrapper.properties
│   ├── Dockerfile Dockerfile.migrate .dockerignore
│   └── src/main/
│       ├── resources/{application.yml,application-dev.yml,application-prod.yml}
│       ├── resources/db/migration/V1__schema_privileges.sql … V7__domains_deployments.sql
│       └── java/com/expertiz/sentinel/core/
│           ├── CoreApiApplication.java
│           ├── shared/                     module ouvert : problem/, web/, time/, audit/, crypto/,
│           │                               secrets/, text/ (validateurs), persistence/, security/Permissions
│           ├── audit/                      AuditLogsController ; internal/ (recorder, résolution de l'acteur, requêtes)
│           ├── identity/                   internal/ : model/, security/, session/, admin/, bootstrap/ ; web/
│           ├── infrastructure/             API publique : ServerCatalog, ServerSummary, ServerUsageChecker, AdminStatus
│           │   └── internal/               Server, ServerService, ServersController, TagRules…
│           └── projects/                   internal/ : projets, environnements, domaines, déploiements, contrôleurs
├── services/core-api/src/test/java/com/expertiz/sentinel/core/
│   ├── support/                            SentinelPostgres, IntegrationTest, IsolatedDatabaseIT, TestClock, Fixtures
│   └── …                                   tests rangés comme le code
├── frontend/
│   ├── package.json vite.config.ts tsconfig*.json eslint.config.js Dockerfile nginx/spa.conf .dockerignore
│   └── src/
│       ├── api/{schema.d.ts,client.ts,authMiddleware.ts,problem.ts,applyProblem.ts}
│       ├── auth/{session.ts,refreshCoordinator.ts,authChannel.ts,AuthProvider.tsx,guards.tsx,
│       │         permissions.ts,LoginPage.tsx,ChangePasswordPage.tsx,ProfilePage.tsx}
│       ├── components/{PaginatedTable.tsx,useListParams.ts,ConfirmDelete.tsx}
│       ├── features/{servers,projects,users,audit,dashboard}/
│       ├── layout/AppLayout.tsx  main.tsx router.tsx theme.ts
│       └── test/{setup.ts,server.ts}
└── tests/e2e/{package.json,playwright.config.ts,helpers/api.ts,journeys.spec.ts,gateway.spec.ts}
```

**Choix d'accès aux données :**

- **JPA** pour les agrégats : `User`, `Server`, `Project`, `Environment`, `Domain`, `Deployment`. La suppression logique passe par `@SQLRestriction("deleted_at IS NULL")`.
- **`JdbcClient`** pour tout ce qui relève de requêtes SQL précises : `refresh_tokens` (rotation conditionnelle), `login_throttles` (insertion ou mise à jour atomique), `audit_logs` (insertion seule), verrous consultatifs, verrou du VPS pour un déploiement, `last_login_at`, facettes.

## Conventions de test (backend)

- **Classe de base `IntegrationTest`.**
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)` et `RestTestClient` (Spring Framework 7) liés au port réel. Les requêtes traversent donc Tomcat, ce qui inclut la valve RemoteIp.
  - Base PostgreSQL 18 partagée, démarrée une seule fois par `SentinelPostgres`.
  - `Clock` remplacé par `TestClock` (`@Primary`).
  - Méthodes utilitaires `ownerJdbc()` (connexion `sentinel_owner`, pour les assertions) et `appJdbc()` (connexion `sentinel_app`, celle de l'application).
- **Pas de nettoyage global** : `audit_logs` ne peut pas être vidée. Chaque test crée des données uniques (email, nom, IP) via `Fixtures`, et ses assertions sur l'audit filtrent par `target_id` ou `correlation_id`.
- **IP client par test** : chaque test envoie `X-Forwarded-For: <Fixtures.uniqueIp()>`. Depuis 127.0.0.1, proxy de confiance par défaut, cela reproduit le gateway, et les compteurs de limitation ne se mélangent pas entre tests.
- **Tests qui supposent un état global** (bootstrap, dernier SUPER_ADMIN) : ils héritent d'`IsolatedDatabaseIT`, qui démarre une base neuve pour la classe.
- Surefire inclut `**/*Test.java` et `**/*IT.java`.

---

## Tâche 0 — Prérequis du poste, versionnement des docs, renommage (opérateur, avec accord explicite)

Aucun code. Ces étapes modifient le poste et le dépôt parent : les exécuter seulement après l'accord explicite de l'utilisateur. Le renommage exige de fermer VS Code, donc cette session.

- [ ] **Étape 1 : versionner la spec v2 et ce plan**, depuis la racine actuelle `vps monitoring/` :

```bash
git add docs/superpowers/specs/2026-11-02-phase1-foundation-design.md docs/superpowers/plans/2026-11-02-phase1-foundation.md
git commit -m "docs: phase 1 spec v2 and implementation plan"
```

- [ ] **Étape 2 : installer le JDK 25.** `winget install --id EclipseAdoptium.Temurin.25.JDK -e`

  Vérifier :

  ```bash
  export JAVA_HOME="$(ls -d "/c/Program Files/Eclipse Adoptium/jdk-25"* | head -1)"
  "$JAVA_HOME/bin/java" -version
  ```

  Attendu : `openjdk version "25`. Ajouter l'`export` à `~/.bashrc`, avec l'accord de l'utilisateur.

- [ ] **Étape 3 : renommer.** Fermer VS Code et les terminaux, puis dans PowerShell : `Rename-Item "C:\Users\exp\Desktop\expertiz\vps monitoring" vps-sentinel`. Rouvrir VS Code sur `C:\Users\exp\Desktop\expertiz\vps-sentinel`.
- [ ] **Étape 4 : exclure le dossier du dépôt parent.** `printf '\nvps-sentinel/\n' >> /c/Users/exp/Desktop/expertiz/.git/info/exclude`
- [ ] **Étape 5 : vérifier.**
  - `git -C /c/Users/exp/Desktop/expertiz status --short | grep -c vps` → `0` ;
  - `git -C /c/Users/exp/Desktop/expertiz/vps-sentinel log --oneline -1` → le commit de l'étape 1 ;
  - `docker info --format '{{.ServerVersion}}'` → au moins `29` ;
  - `node -v` → `v24`.

## Tâche 1 — Hygiène du dépôt et contrôle automatique

**Fichiers :**

- Créer : `.gitattributes`, `.gitignore`, `.editorconfig`, `README.md` (squelette), `scripts/check-repo.sh`.

**Interfaces :**

- Produit : `scripts/check-repo.sh`, qui sort avec un code non nul en cas d'écart. Il est réutilisé par la CI (tâche 25).

- [ ] **Étape 1 : écrire `scripts/check-repo.sh`** (Bash, `set -euo pipefail`, chemins entre guillemets). Il échoue si l'une de ces conditions est vraie :
  - (a) `git ls-files --eol` signale `i/crlf` ou `w/crlf` pour un fichier autre que `*.cmd` et `*.bat` ;
  - (b) `git ls-files -s` donne un mode autre que `100755` pour `services/core-api/mvnw` ou pour un fichier `scripts/*.sh` présent ;
  - (c) `.gitattributes`, `.gitignore` ou `.editorconfig` est absent.

  Chaque écart est affiché en clair avant la sortie en erreur.
- [ ] **Étape 2 : lancer `bash scripts/check-repo.sh`.** Attendu : échec, « .gitattributes absent ».
- [ ] **Étape 3 : créer les fichiers d'hygiène.**
  - `.gitattributes` et `.gitignore` : contenu exact de la spec §5.3.
  - `.editorconfig` : `root = true` ; `[*]` avec `end_of_line = lf`, `charset = utf-8`, `insert_final_newline = true`, `indent_style = space`, `indent_size = 2` ; `[*.java]` avec `indent_size = 4` ; `[*.{cmd,bat}]` avec `end_of_line = crlf`.
  - `README.md` : description d'une ligne, encadré « Phase 1 non autorisée en production — voir spec §15 », règle « jamais de `git add .` depuis `expertiz` ».
- [ ] **Étape 4 : poser le bit exécutable et relancer.**

  ```bash
  git add .gitattributes .gitignore .editorconfig README.md scripts/check-repo.sh
  git update-index --chmod=+x scripts/check-repo.sh
  bash scripts/check-repo.sh
  ```

  Attendu : `OK`.
- [ ] **Étape 5 : commit.** `git commit -m "chore: repository hygiene files and checks"`

## Tâche 2 — Squelette de core-api et frontières de modules

**Fichiers :**

- Créer : `services/core-api/` à partir de Spring Initializr, puis `pom.xml` ajusté, `.dockerignore`, `CoreApiApplication.java`, un `package-info.java` par module, `application.yml`, `application-dev.yml`, `application-prod.yml`, `src/test/java/.../ModularityTest.java`.

**Interfaces :**

- Produit :
  - les packages `…core.shared` (déclaré `@ApplicationModule(type = ApplicationModule.Type.OPEN)`), `…core.identity`, `…core.audit`, `…core.infrastructure`, `…core.projects` ;
  - les propriétés `sentinel.*` : `sentinel.security.allowed-origins` (List), `sentinel.security.refresh-cookie-secure` (bool), `sentinel.security.access-token-ttl=PT15M`, `refresh-idle-ttl=PT24H`, `session-max-age=P7D`, `temporary-password-ttl=PT72H`, `sentinel.secrets.dir=/run/secrets`, `sentinel.bootstrap.admin.{email,password,name}`.

- [ ] **Étape 1 : générer le projet.**

  ```bash
  curl -s -o /tmp/core.zip "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.2&javaVersion=25&groupId=com.expertiz.sentinel&artifactId=core-api&name=core-api&packageName=com.expertiz.sentinel.core&dependencies=web,security,oauth2-resource-server,data-jpa,validation,actuator,flyway,postgresql,testcontainers,modulith"
  ```

  Décompresser dans `services/core-api/`. Vérifier que `.mvn/wrapper/maven-wrapper.properties` contient `distributionType=only-script` et qu'aucun `.jar` n'est présent.
- [ ] **Étape 2 : ajuster `pom.xml`.**
  - **Retirer :** `spring-modulith-starter-jpa`, `spring-modulith-observability-*`, `spring-modulith-runtime`, `spring-modulith-actuator`. Pas de registre d'événements : l'audit est synchrone.
  - **Garder :** `spring-boot-starter-{webmvc,security,security-oauth2-resource-server,data-jpa,validation,actuator,flyway}`, `flyway-database-postgresql`, `postgresql` (runtime), `spring-modulith-starter-core`.
  - **Tests :** `spring-boot-starter-{webmvc,security}-test`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`, `spring-modulith-starter-test`.
  - **Ajouter :** `springdoc-openapi-starter-webmvc-api:3.2.0`, `bcprov-jdk18on:1.86`, `java-uuid-generator:5.2.0`.
  - **Surefire :** `<includes>` limité à `**/*Test.java` et `**/*IT.java`.
- [ ] **Étape 3 : écrire le test de frontières.**

```java
class ModularityTest {
  static final ApplicationModules MODULES = ApplicationModules.of(CoreApiApplication.class);

  @Test void modulesRespectBoundaries() { MODULES.verify(); }

  @Test void expectedModulesExist() {
    assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
        .containsExactlyInAnyOrder("shared", "identity", "audit", "infrastructure", "projects");
  }
}
```

- [ ] **Étape 4 : lancer `./mvnw -q test -Dtest=ModularityTest`.** Attendu : échec, les packages n'existent pas encore.
- [ ] **Étape 5 : créer `CoreApiApplication` et les 5 `package-info.java`.**

  `application.yml` :
  - datasource : utilisateur `sentinel_app`, mot de passe `${SENTINEL_DB_APP_PASSWORD}`. L'URL est définie par profil : `jdbc:postgresql://postgres:5432/sentinel?currentSchema=sentinel` en `prod` (conteneur), `jdbc:postgresql://localhost:5432/sentinel?currentSchema=sentinel` en `dev` (API lancée depuis l'IDE). Les tests la remplacent ;
  - `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` ;
  - Flyway : mêmes identifiants applicatifs, `spring.flyway.schemas=sentinel`, `spring.flyway.create-schemas=false` ;
  - `server.forward-headers-strategy=native` ;
  - `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never` ;
  - `sentinel.security.allowed-origins=${SENTINEL_ALLOWED_ORIGINS:}`, `refresh-cookie-secure=${SENTINEL_REFRESH_COOKIE_SECURE:true}`, bootstrap depuis les variables du spec §6.6.

  `application-prod.yml` : `springdoc.api-docs.enabled=false`, `logging.structured.format.console=ecs`.

  `application-dev.yml` : origines par défaut `http://localhost:5173`.
- [ ] **Étape 6 : lancer `./mvnw -q test -Dtest=ModularityTest`.** Attendu : PASS.
- [ ] **Étape 7 : commit.**

  ```bash
  git add services/core-api
  git update-index --chmod=+x services/core-api/mvnw
  bash scripts/check-repo.sh
  git commit -m "feat(core-api): project skeleton and module boundaries"
  ```

## Tâche 3 — Rôles PostgreSQL, première migration, harnais Testcontainers

**Fichiers :**

- Créer : `infrastructure/postgres/init/01-roles.sh`, `db/migration/V1__schema_privileges.sql`, `shared/persistence/FlywayValidateOnlyConfig.java`.
- Tests : `support/SentinelPostgres.java`, `support/IntegrationTest.java`, `support/IsolatedDatabaseIT.java`, `support/TestClock.java`, `support/TestClockConfig.java`, `persistence/DatabasePrivilegesIT.java`, `ApplicationStartupIT.java`, `shared/persistence/FlywayValidateOnlyConfigTest.java`.

**Interfaces :**

- Produit :
  - `SentinelPostgres.shared()` → conteneur unique ; `newIsolated()` → nouveau conteneur ;
  - pour chaque instance : `appJdbcUrl()`, `ownerDataSource()`, `migrate()` ;
  - `TestClock extends Clock` avec `set(Instant)` et `advance(Duration)` ;
  - `IntegrationTest` avec `http` (`RestTestClient`), `ownerJdbc()`, `appJdbc()`, `clock` ;
  - `FlywayMigrationStrategy` qui appelle `flyway.validate()`.

- [ ] **Étape 1 : écrire `01-roles.sh`.**
  - Bash, `set -euo pipefail`, `psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER"`.
  - Les mots de passe passent en variables psql pour éviter toute injection : `-v owner_pw="$SENTINEL_DB_OWNER_PASSWORD" -v app_pw="$SENTINEL_DB_APP_PASSWORD"`.
  - Instructions, dans cet ordre :

```sql
-- base postgres
CREATE ROLE sentinel_owner LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB PASSWORD :'owner_pw';
CREATE ROLE sentinel_app   LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB PASSWORD :'app_pw';
CREATE DATABASE sentinel OWNER sentinel_owner;
REVOKE CONNECT ON DATABASE sentinel FROM PUBLIC;
GRANT CONNECT ON DATABASE sentinel TO sentinel_app;
-- \connect sentinel
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA sentinel AUTHORIZATION sentinel_owner;
GRANT USAGE ON SCHEMA sentinel TO sentinel_app;
ALTER ROLE sentinel_owner SET search_path = sentinel;
ALTER ROLE sentinel_app   SET search_path = sentinel;
```

- [ ] **Étape 2 : écrire `V1__schema_privileges.sql`.**

```sql
ALTER DEFAULT PRIVILEGES IN SCHEMA sentinel GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA sentinel GRANT USAGE, SELECT ON SEQUENCES TO sentinel_app;
GRANT SELECT ON flyway_schema_history TO sentinel_app;
```

- [ ] **Étape 3 : écrire le harnais.**
  - `SentinelPostgres` utilise `org.testcontainers.postgresql.PostgreSQLContainer` sur `postgres:18`.
  - Il passe `POSTGRES_PASSWORD`, `SENTINEL_DB_OWNER_PASSWORD=owner-test` et `SENTINEL_DB_APP_PASSWORD=app-test`, et copie `../../infrastructure/postgres/init/01-roles.sh` dans `/docker-entrypoint-initdb.d/`.
  - `migrate()` lance Flyway en Java avec `sentinel_owner`, `schemas("sentinel")`, `createSchemas(false)`, `locations("classpath:db/migration")`.
  - `IntegrationTest` déclare un `@DynamicPropertySource` qui fournit l'URL, `sentinel_app` et `app-test` à `spring.datasource.*` et à `spring.flyway.*`.
- [ ] **Étape 4 : écrire les tests.**

```java
class DatabasePrivilegesIT extends IntegrationTest {
  @Test void appRoleCannotCreateTables() {
    assertThatThrownBy(() -> appJdbc().sql("CREATE TABLE x(id int)").update()).hasMessageContaining("permission denied");
  }
  @Test void appRoleCannotUsePublicSchema() {
    assertThatThrownBy(() -> appJdbc().sql("CREATE TABLE public.x(id int)").update()).hasMessageContaining("permission denied");
  }
  @Test void appRoleReadsButCannotWriteFlywayHistory() {
    assertThat(appJdbc().sql("SELECT count(*) FROM flyway_schema_history").query(Long.class).single()).isPositive();
    assertThatThrownBy(() -> appJdbc().sql("UPDATE flyway_schema_history SET success = true").update()).hasMessageContaining("permission denied");
  }
}
class ApplicationStartupIT extends IntegrationTest {
  @Test void healthIsUpWithoutDetails() {
    http.get().uri("/actuator/health").exchange().expectStatus().isOk()
        .expectBody().jsonPath("$.status").isEqualTo("UP").jsonPath("$.components").doesNotExist();
  }
}
class FlywayValidateOnlyConfigTest {
  @Test void strategyValidatesAndNeverMigrates() {
    Flyway flyway = mock(Flyway.class);
    new FlywayValidateOnlyConfig().validateOnly().migrate(flyway);
    verify(flyway).validate(); verify(flyway, never()).migrate();
  }
}
```

- [ ] **Étape 5 : lancer `./mvnw -q test -Dtest='DatabasePrivilegesIT,ApplicationStartupIT,FlywayValidateOnlyConfigTest'`.** Attendu : échec (harnais ou configuration absents).
- [ ] **Étape 6 : implémenter `FlywayValidateOnlyConfig#validateOnly()`.** Un `@Bean FlywayMigrationStrategy` égal à `Flyway::validate`.
- [ ] **Étape 7 : relancer.** Attendu : PASS.
- [ ] **Étape 8 : commit.**

  ```bash
  git update-index --chmod=+x infrastructure/postgres/init/01-roles.sh
  git commit -m "feat(db): separated postgres roles, first migration, test harness"
  ```

## Tâche 4 — Compose : postgres, migrate, core-api ; secrets de dev

**Fichiers :**

- Créer : `services/core-api/Dockerfile`, `services/core-api/Dockerfile.migrate`, `docker-compose.yml`, `docker-compose.dev.yml`, `.env.example`, `scripts/gen-dev-secrets.sh`.

**Interfaces :**

- Produit :
  - services Compose `postgres`, `migrate`, `core-api` ;
  - réseaux `public`, `app`, `data` ; volume `pgdata` ;
  - secrets Compose `jwt_private`, `jwt_public`, `hmac_key`, montés dans `/run/secrets/jwt-private.pem`, `jwt-public.pem` et `hmac.key`.

- [ ] **Étape 1 : `scripts/gen-dev-secrets.sh`.**
  - Il crée `infrastructure/secrets/dev/`.
  - Commandes : `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem`, puis `openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem`, puis `openssl rand -base64 32 > hmac.key`.
  - Il n'écrase rien sans l'option `--force`.
- [ ] **Étape 2 : `Dockerfile` de core-api.**
  - **Étape de build :** `eclipse-temurin:25-jdk`. Copier `.mvn`, `mvnw` et `pom.xml`, puis `RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline`. Copier `src`, puis `sh ./mvnw -B -q package -DskipTests`.
  - **Étape d'exécution :** `eclipse-temurin:25-jre`, avec `apt-get install -y --no-install-recommends curl` pour le healthcheck. Utilisateur non root `10001`.
  - `HEALTHCHECK` : `curl -fsS http://localhost:8080/actuator/health`.
- [ ] **Étape 3 : `Dockerfile.migrate`.** `FROM flyway/flyway:12.4.0-alpine`, puis `COPY src/main/resources/db/migration /flyway/sql`, puis `CMD ["migrate"]`.
- [ ] **Étape 4 : `docker-compose.yml` (`name: vps-sentinel`).**
  - **postgres :** reçoit `POSTGRES_PASSWORD`, `SENTINEL_DB_OWNER_PASSWORD` et `SENTINEL_DB_APP_PASSWORD`. Il monte `./infrastructure/postgres/init` en lecture seule. Healthcheck `pg_isready -U postgres`. Réseau `data`.
  - **migrate :** `FLYWAY_URL=jdbc:postgresql://postgres:5432/sentinel`, `FLYWAY_USER=sentinel_owner`, `FLYWAY_PASSWORD=${SENTINEL_DB_OWNER_PASSWORD}`, `FLYWAY_SCHEMAS=sentinel`, `FLYWAY_CONNECT_RETRIES=10`. Dépend de `postgres` (`service_healthy`). Réseau `data`.
  - **core-api :** `SPRING_PROFILES_ACTIVE=prod`, `SENTINEL_DB_APP_PASSWORD`, origines, cookie, variables de bootstrap. Les trois secrets. Dépend de `migrate` (`service_completed_successfully`). Réseaux `app` et `data`.
  - **Aucun port publié.**
- [ ] **Étape 5 : `docker-compose.dev.yml`.** `postgres` publie `5432:5432`. Les secrets ont pour `file: ./infrastructure/secrets/dev/...`.
- [ ] **Étape 6 : `.env.example`.**

```dotenv
POSTGRES_PASSWORD=change-me-superuser
SENTINEL_DB_OWNER_PASSWORD=change-me-owner
SENTINEL_DB_APP_PASSWORD=change-me-app
SENTINEL_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:8088
SENTINEL_REFRESH_COOKIE_SECURE=true
SENTINEL_BOOTSTRAP_ADMIN_EMAIL=admin@sentinel.local
SENTINEL_BOOTSTRAP_ADMIN_PASSWORD=Bootstrap-Dev-2026!
SENTINEL_BOOTSTRAP_ADMIN_NAME=Administrateur
SENTINEL_SECRETS_DIR=./infrastructure/secrets/prod
SENTINEL_TLS_CERT_DIR=
SENTINEL_ACME_WEBROOT=
```

- [ ] **Étape 7 : vérifier.**

  ```bash
  cp .env.example .env
  bash scripts/gen-dev-secrets.sh
  docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build postgres migrate core-api
  ```

  Attendu, une fois les conteneurs stabilisés :
  - `docker compose -f docker-compose.yml -f docker-compose.dev.yml ps -a` : `migrate` en `Exited (0)` ;
  - `docker inspect --format '{{.State.Health.Status}}' vps-sentinel-core-api-1` : `healthy`.

  Puis `docker compose … down -v`.
- [ ] **Étape 8 : commit.** `git update-index --chmod=+x scripts/gen-dev-secrets.sh`, puis `git commit -m "feat(infra): compose data plane, migration container, dev secrets"`.

## Tâche 5 — Noyau partagé : erreurs, pagination, corrélation, horloge

**Fichiers :**

- Créer, dans `shared/` :
  - `problem/ProblemType.java`, `problem/ApiException.java`, `problem/FieldViolation.java`, `problem/ProblemDetailsAdvice.java`, `problem/ProblemWriter.java`, `problem/ConstraintMapping.java` ;
  - `web/CorrelationIdFilter.java`, `web/PageResponse.java`, `web/SortSpec.java`, `web/PageRequests.java`, `web/RequestInfo.java` ;
  - `time/ClockConfig.java`.
- Tests : `shared/problem/ProblemDetailsAdviceTest.java` (`@WebMvcTest`, filtres de sécurité désactivés, contrôleur de test dans le même fichier), `shared/web/CorrelationIdFilterTest.java`, `shared/web/PageRequestsTest.java`.

**Interfaces :**

- Produit :
  - `enum ProblemType { VALIDATION_ERROR("validation-error",400), UNAUTHORIZED("unauthorized",401), FORBIDDEN("forbidden",403), ORIGIN_NOT_ALLOWED("origin-not-allowed",403), PASSWORD_CHANGE_REQUIRED("password-change-required",403), NOT_FOUND("not-found",404), UNSUPPORTED_MEDIA_TYPE("unsupported-media-type",415), VERSION_CONFLICT("version-conflict",409), LAST_SUPER_ADMIN("last-super-admin",409), SELF_MODIFICATION_FORBIDDEN("self-modification-forbidden",409), SERVER_IN_USE("server-in-use",409), SERVER_NOT_DEPLOYABLE("server-not-deployable",409), DEPLOYMENT_EXISTS("deployment-exists",409), DUPLICATE("duplicate",409), LOGIN_THROTTLED("login-throttled",429), RATE_LIMITED("rate-limited",429), INTERNAL_ERROR("internal-error",500); String code(); int status(); URI type(); }` ;
  - `ApiException(ProblemType type, String detail)` et `ApiException(ProblemType type, String detail, List<FieldViolation> errors)` ; `record FieldViolation(String field, String message)` ;
  - `ProblemWriter#write(HttpServletResponse res, ProblemType type, String detail)`, pour les filtres et les handlers de sécurité ;
  - `record ConstraintMapping(String constraint, ProblemType type, String field)`. Chaque module déclare des beans de ce type, et l'advice les collecte ;
  - `record SortSpec(Map<String,String> apiToProperty, String defaultSort)` ; `PageRequests.of(Integer page, Integer size, String sort, SortSpec spec) : Pageable` ;
  - `record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages)` avec `static <T> PageResponse<T> from(Page<T> page)` ;
  - `RequestInfo.clientIp() : Optional<String>`, `RequestInfo.userAgent() : Optional<String>` (tronqué à 255) ;
  - MDC `correlationId`, en-tête `X-Request-Id` ;
  - `@Bean Clock clock()` : `Clock.systemUTC()`.

- [ ] **Étape 1 : écrire les tests.**

```java
// ProblemDetailsAdviceTest — contrôleur de test sous /test/*
@Test void apiExceptionRendered() {        // GET /test/problem → throw new ApiException(SERVER_IN_USE, "VPS utilisé par des déploiements")
  assertThat(mvc.get().uri("/test/problem")).hasStatus(409)
      .hasContentType("application/problem+json")
      .bodyJson().hasPathSatisfying("$.type", v -> v.assertThat().isEqualTo("urn:sentinel:problem:server-in-use"))
      .hasPathSatisfying("$.correlationId", v -> v.assertThat().isNotNull());
}
@Test void fieldErrorsListed()              { /* POST /test/validated {"name":""} → 400, $.errors[0].field == "name" */ }
@Test void unreadableBodyNotEchoed()        { /* POST corps JSON tronqué contenant "S3cr3t-Passw0rd!" → 400 validation-error ; le corps de réponse ne contient pas "S3cr3t" */ }
@Test void textPlainIs415()                 { /* → 415, $.type urn:sentinel:problem:unsupported-media-type */ }
@Test void unexpectedErrorHasNoDetails()    { /* RuntimeException("boom-secret") → 500 internal-error ; corps sans "boom-secret" ni "RuntimeException" */ }
@Test void optimisticLockIsVersionConflict(){ /* ObjectOptimisticLockingFailureException → 409 version-conflict */ }
@Test void uniqueViolationMapsToField()     { /* DataIntegrityViolationException avec cause PSQLException, contrainte "uq_test_name" ; bean ConstraintMapping("uq_test_name", DUPLICATE, "name") → 409 duplicate, $.errors[0].field == "name" */ }
// CorrelationIdFilterTest
@Test void reusesValidIncomingId()  { /* X-Request-Id "abc123" → MDC et en-tête de réponse "abc123" */ }
@Test void generatesWhenMissing()   { /* en-tête de réponse conforme à [0-9a-f]{32} */ }
@Test void replacesInvalidId()      { /* 65 caractères, ou espace → nouvel identifiant de 32 caractères hexadécimaux */ }
// PageRequestsTest
@Test void defaults()              { /* page 0, size 20, tri par défaut de la SortSpec */ }
@Test void rejectsSizeOver100()    { /* ApiException VALIDATION_ERROR, champ "size" */ }
@Test void rejectsNegativePage()   { /* champ "page" */ }
@Test void rejectsUnknownSort()    { /* "description,asc" hors liste → champ "sort" */ }
@Test void mapsApiFieldToProperty(){ /* "createdAt,desc" → Sort.by(DESC, "createdAt") */ }
```

- [ ] **Étape 2 : lancer les trois classes.** Attendu : échec de compilation.
- [ ] **Étape 3 : implémenter.**
  - L'advice associe chaque exception Spring à son `ProblemType`. Le `detail` de `HttpMessageNotReadableException` est fixe : « Corps de requête invalide. ». Les exceptions inconnues sont journalisées avec le `correlationId`, puis renvoyées en `internal-error`.
  - `CorrelationIdFilter` est un `OncePerRequestFilter` en `@Order(Ordered.HIGHEST_PRECEDENCE)`. Il accepte un identifiant de 1 à 64 caractères dans `[A-Za-z0-9-]`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(shared): problem details, pagination, correlation id, clock"`

## Tâche 6 — Empreintes HMAC, secrets, règles de texte et validateurs

**Fichiers :**

- Créer, dans `shared/` : `secrets/SentinelSecrets.java`, `crypto/Fingerprints.java`, `text/TextRules.java`, `text/NoControlChars.java` (avec son validateur), `text/Hostname.java` (avec son validateur), `text/HttpUrl.java` (avec son validateur), `persistence/UuidV7.java`, `persistence/AssignedIdEntity.java`.
- Tests : `FingerprintsTest`, `SentinelSecretsTest`, `HostnameValidatorTest`, `HttpUrlValidatorTest`, `TextRulesTest`, `support/TestSecrets.java`.
- Modifier : `support/IntegrationTest.java`. Son `@DynamicPropertySource` fixe `sentinel.secrets.dir` sur `TestSecrets.directory()`, un dossier temporaire contenant une paire RSA et une clé HMAC générées une seule fois par JVM. Sans cela, le chargeur ferait échouer le démarrage de tous les tests d'intégration ; et le vrai chemin de lecture est ainsi testé.

**Interfaces :**

- Produit :
  - **`SentinelSecrets` (bean) :** `RSAPrivateKey jwtPrivateKey()`, `RSAPublicKey jwtPublicKey()`, `byte[] hmacKey()`. Il lit `${sentinel.secrets.dir}`. En profil `dev`, si un fichier manque, il génère une paire RSA 3072 et une clé de 32 octets éphémères, avec un log `WARN`. Ailleurs, il lève `IllegalStateException("Secret manquant : <fichier>")`.
  - **`Fingerprints` (bean) :**
    - `String identifier(String raw)` : HMAC-SHA256 avec la clé, appliqué à `"id:" + normalizeIdentifier(raw)`, tronqué à 16 octets, en hexadécimal minuscule ;
    - `String ip(String ip)` : même calcul avec le préfixe `"ip:"` ;
    - `static String normalizeIdentifier(String raw)` : suppression des espaces aux extrémités, NFKC, `toLowerCase(Locale.ROOT)`.
  - **`TextRules.clean(String s) : String`** : supprime les espaces aux extrémités ; une chaîne vide devient `null`.
  - **Validateurs :**
    - `@NoControlChars(allowNewlines = false)` ;
    - `@Hostname(requireDot = false)` : insensible à la casse, la normalisation en minuscules se fait dans les services ;
    - `@HttpUrl` : URL absolue en `http` ou `https`, sans `user:pass@`.
  - **`UuidV7.next() : UUID`** (`Generators.timeBasedEpochGenerator()`).
  - **`AssignedIdEntity`** (`@MappedSuperclass`, `Persistable<UUID>`) : `id` attribué dans le constructeur ; `isNew` transitoire, remis à faux en `@PostLoad` et `@PostPersist`.

- [ ] **Étape 1 : écrire les tests.**

```java
@Test void identifierIsCaseAndSpaceInsensitive() { assertThat(fp.identifier("  Alice@Example.COM ")).isEqualTo(fp.identifier("alice@example.com")); }
@Test void identifierAppliesNfkc()               { assertThat(fp.identifier("ａｌｉｃｅ@example.com")).isEqualTo(fp.identifier("alice@example.com")); }
@Test void fingerprintFormat()                   { assertThat(fp.identifier("x")).matches("[0-9a-f]{32}"); }
@Test void domainsSeparated()                    { assertThat(fp.identifier("10.0.0.1")).isNotEqualTo(fp.ip("10.0.0.1")); }
@Test void keyMatters()                          { assertThat(new Fingerprints(keyA).identifier("a")).isNotEqualTo(new Fingerprints(keyB).identifier("a")); }
// SentinelSecretsTest (dossier temporaire)
@Test void loadsFromDirectory(); @Test void rejectsHmacKeyUnder32Bytes(); 
@Test void prodFailsWhenMissing() { assertThatThrownBy(...).hasMessageContaining("jwt-private.pem"); }
@Test void devGeneratesEphemeral();
// HostnameValidatorTest (paramétré)
// valides  (requireDot=false) : "vps-01", "VPS-01.example.com", "a.b"
// valides  (requireDot=true)  : "demo.example.com", "xn--bcher-kva.example"
// invalides : "-bad.com", "bad-.com", "bad..com", "demo.example.com." , "*.example.com", "bücher.example",
//             label de 64 caractères, nom de 254 caractères ; "example" invalide seulement si requireDot=true
// HttpUrlValidatorTest : valide "https://github.com/org/repo" ; invalides "javascript:alert(1)", "ftp://x/y",
//                        "https://user:pass@host/x", "//host/x", "github.com/x"
// TextRulesTest : clean("  a  ") == "a" ; clean("   ") == null ; @NoControlChars refuse "\u0007" ;
//                 @NoControlChars(allowNewlines=true) accepte "a\nb\tc"
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter.** HMAC via `javax.crypto.Mac` (`HmacSHA256`). Lecture PEM via `KeyFactory` (PKCS#8 pour la clé privée, X.509 pour la publique).
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(shared): hmac fingerprints, secrets loading, text validators"`

## Tâche 7 — Module audit : écriture et protection en base

**Fichiers :**

- Créer : `V2__audit_logs.sql`, `shared/audit/AuditEvent.java`, `shared/audit/AuditActor.java`, `shared/audit/ActorType.java`, `shared/audit/AuditDiff.java`, `audit/internal/AuditRecorder.java`, `audit/internal/AuditActorResolver.java`.
- Tests : `audit/AuditTableProtectionIT.java`, `audit/AuditRecorderIT.java`, `shared/audit/AuditDiffTest.java`.

**Interfaces :**

- Produit :
  - `record AuditEvent(String action, String targetType, UUID targetId, Map<String,Object> details, AuditActor actor)`, avec `static AuditEvent of(String action, String targetType, UUID targetId, Map<String,Object> details)` (acteur nul, donc résolu automatiquement) et `AuditEvent withActor(AuditActor actor)` ;
  - `record AuditActor(ActorType type, UUID userId, String email)`, avec `anonymous()`, `system()`, `user(UUID, String)` ;
  - `enum ActorType { USER, SYSTEM, ANONYMOUS }` ;
  - `AuditDiff.changes(Map<String,Object> before, Map<String,Object> after) : Map<String,Map<String,Object>>`, au format `{"champ": {"from": …, "to": …}}` ;
  - publication par `ApplicationEventPublisher#publishEvent(AuditEvent)`. **Règle :** chaque agrégat expose `Map<String,Object> auditState()`, qui ne contient que ses champs auditables (liste blanche, spec §9.5).

- [ ] **Étape 1 : écrire `V2__audit_logs.sql`.**
  - Colonnes exactes du spec §9.5.
  - Index `ix_audit_logs_occurred_at (occurred_at DESC)`, `ix_audit_logs_actor (actor_user_id)`, `ix_audit_logs_action (action)`, `ix_audit_logs_target (target_type, target_id)`.
  - Puis :

```sql
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM sentinel_app;
CREATE FUNCTION audit_logs_reject_change() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION 'audit_logs is append-only'; END $$;
CREATE TRIGGER audit_logs_no_update_delete BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_change();
CREATE TRIGGER audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
  FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_change();
```

- [ ] **Étape 2 : écrire les tests.**

```java
// AuditTableProtectionIT
@Test void ownerUpdateDeleteTruncateRejectedByTrigger() { /* ownerJdbc : les 3 instructions → message "audit_logs is append-only" */ }
@Test void appRoleHasNoUpdateDeleteTruncate()           { /* appJdbc : les 3 instructions → "permission denied" */ }
@Test void appRoleCannotAlterOrDrop()                   { /* appJdbc : ALTER TABLE audit_logs ADD COLUMN x int, puis DROP TABLE audit_logs → "must be owner" ou "permission denied" */ }
// AuditRecorderIT (publication via un TransactionTemplate)
@Test void committedTransactionWritesRow()   { /* ligne avec action, target, details jsonb, occurred_at == clock.instant() */ }
@Test void rolledBackTransactionWritesNothing() { /* publication puis setRollbackOnly → 0 ligne pour ce target_id */ }
@Test void publishingOutsideTransactionFails()  { /* IllegalTransactionStateException (propagation MANDATORY) */ }
@Test void explicitAnonymousActorStoredWithoutEmail() { /* actor_type ANONYMOUS, actor_email NULL, actor_user_id NULL */ }
@Test void requestIpAndCorrelationRecorded() { /* sans HTTP (la sécurité n'existe qu'à la tâche 9) : RequestContextHolder lié à un MockHttpServletRequest (remoteAddr 10.9.8.7) et MDC correlationId "corr-1" → ip 10.9.8.7, correlation_id corr-1 */ }
// AuditDiffTest
@Test void onlyChangedKeys(); @Test void nullToValueAndValueToNull();
```

- [ ] **Étape 3 : lancer les tests.** Attendu : échec.
- [ ] **Étape 4 : implémenter.**
  - `AuditRecorder` : `@EventListener` avec `@Transactional(propagation = MANDATORY)`. Insertion via `JdbcClient`, `id = UuidV7.next()`, `occurred_at = clock.instant()`, `details` sérialisé en `jsonb` (Jackson 3).
  - `AuditActorResolver` : si l'acteur est explicite, il est utilisé. Sinon, un `JwtAuthenticationToken` donne `USER` (`sub`, claim `email`). Sinon, `ANONYMOUS`.
- [ ] **Étape 5 : relancer.** Attendu : PASS.
- [ ] **Étape 6 : commit.** `git commit -m "feat(audit): synchronous append-only audit log"`

## Tâche 8 — Identité : schéma, matrice, mots de passe

**Fichiers :**

- Créer : `V3__identity.sql`, `V4__sessions.sql`, `shared/security/Permissions.java`, et dans `identity/internal/model/` : `User.java`, `Role.java`, `RoleCode.java`, `UserStatus.java`, `UserRepository.java`, `PermissionMatrix.java`, `PasswordPolicy.java`, `TemporaryPasswordGenerator.java`, `PasswordConfig.java`.
- Tests : `PermissionMatrixIT`, `PasswordPolicyTest`, `TemporaryPasswordGeneratorTest`, `UserPersistenceIT`.

**Interfaces :**

- Produit :
  - **`Permissions`** : constantes `SERVER_READ="server:read"`, `SERVER_WRITE`, `SERVER_DELETE`, `PROJECT_READ`, `PROJECT_WRITE`, `PROJECT_DELETE`, `USER_READ`, `USER_MANAGE`, `AUDIT_READ`.
  - **`enum RoleCode`** `{ SUPER_ADMIN, INFRA_ADMIN, DEVOPS, DEVELOPER, VIEWER }` ; **`enum UserStatus`** `{ ACTIVE, DISABLED }`.
  - **`User extends AssignedIdEntity`** :
    - champs : `email`, `fullName`, `passwordHash`, `passwordExpiresAt`, `status`, `mustChangePassword`, `lastLoginAt`, `createdAt`, `updatedAt`, `deletedAt`, `@Version Long version` ;
    - rôle : `@ManyToOne @JoinTable(name="user_roles", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id")) Role role` ;
    - `Map<String,Object> auditState()` sur la liste blanche `email`, `fullName`, `role`, `status`, `mustChangePassword`.
  - **`PermissionMatrix.permissionsOf(RoleCode) : Set<String>`** : chargée depuis la base au démarrage, immuable.
  - **`PasswordPolicy.check(String field, String password, String email)`** : lève `ApiException(VALIDATION_ERROR, …, [FieldViolation(field, message)])`.
  - **`TemporaryPasswordGenerator`** : `static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"` ; constructeur `TemporaryPasswordGenerator(SecureRandom)` ; `String generate()` (16 caractères).
  - **`PasswordEncoder`** : `DelegatingPasswordEncoder` avec `argon2` par défaut, implémenté par `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`.

- [ ] **Étape 1 : écrire `V3__identity.sql`.**
  - Tables du spec §9.2 : `users`, `roles`, `permissions`, `role_permissions`, `user_roles`.
  - Puis :

```sql
CREATE UNIQUE INDEX uq_users_email_active ON users (email) WHERE deleted_at IS NULL;
ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles_user UNIQUE (user_id);
-- INSERT des 5 rôles, des 9 permissions et de la matrice du spec §8.12 (codes exacts)
REVOKE INSERT, UPDATE, DELETE ON roles, permissions, role_permissions FROM sentinel_app;
```

- [ ] **Étape 2 : écrire `V4__sessions.sql`.**
  - Tables `refresh_tokens` et `login_throttles` du spec §9.2, plus `ix_refresh_tokens_user (user_id)`, `ix_refresh_tokens_family (family_id)`, `ix_login_throttles_account (account_fp)`.
  - Puis :

```sql
CREATE UNIQUE INDEX uq_login_throttles_key ON login_throttles (scope, coalesce(account_fp, ''), ip_fp);
ALTER TABLE login_throttles ADD CONSTRAINT ck_login_throttles_scope CHECK (
  (scope = 'IP' AND account_fp IS NULL) OR (scope = 'ACCOUNT_IP' AND account_fp IS NOT NULL));
```

- [ ] **Étape 3 : écrire les tests.**

```java
// PermissionMatrixIT — table attendue recopiée du spec §8.12
@ParameterizedTest @EnumSource(RoleCode.class) void matrixMatchesSpec(RoleCode role) {
  assertThat(matrix.permissionsOf(role)).containsExactlyInAnyOrderElementsOf(EXPECTED.get(role));
}
@ParameterizedTest @CsvSource({"roles,INSERT","roles,UPDATE","roles,DELETE","permissions,INSERT","permissions,UPDATE",
    "permissions,DELETE","role_permissions,INSERT","role_permissions,UPDATE","role_permissions,DELETE"})
void appRoleCannotAlterMatrix(String table, String op) { /* appJdbc : l'instruction op sur table → "permission denied" */ }
// PasswordPolicyTest
@Test void rejects11Accepts12(); @Test void rejects129CodePoints();
@Test void countsCodePoints() { /* "😀".repeat(12) accepté */ }
@Test void rejectsEmailIgnoringCase() { /* "Alice@Example.com" pour l'email alice@example.com → violation */ }
// TemporaryPasswordGeneratorTest
@Test void alphabetHas56DistinctSymbolsWithoutAmbiguous() {
  assertThat(ALPHABET.chars().distinct().count()).isEqualTo(56);
  assertThat(ALPHABET).doesNotContainAnyWhitespaces().doesNotContain("0","O","o","1","l","I");
}
@Test void generates16AlphabetChars() { /* 1000 tirages : longueur 16, uniquement des caractères de l'ALPHABET, tous distincts */ }
// UserPersistenceIT
@Test void emailUniqueAmongActiveOnly() { /* 2e utilisateur actif même email → DataIntegrityViolationException ; après deleted_at, création acceptée */ }
@Test void idsAreUuidV7() { /* user.getId().version() == 7 */ }
```

- [ ] **Étape 4 : lancer les tests.** Attendu : échec.
- [ ] **Étape 5 : implémenter** les entités, le repository et les composants listés.
- [ ] **Étape 6 : relancer.** Attendu : PASS.
- [ ] **Étape 7 : commit.** `git commit -m "feat(identity): schema, fixed permission matrix, password policy"`

## Tâche 9 — Jetons d'accès et configuration Spring Security

**Fichiers :**

- Créer :
  - dans `identity/internal/security/` : `JwtConfig.java`, `JwtClaims.java`, `AccessTokenIssuer.java`, `IssuedAccessToken.java`, `SecurityConfig.java`, `ProblemAuthenticationEntryPoint.java`, `ProblemAccessDeniedHandler.java`, `OriginCheckFilter.java`, `PasswordChangeRequiredFilter.java`, `SentinelSecurityProperties.java` ;
  - `identity/web/MeController.java`, `identity/web/MeResponse.java`.
- Tests : `support/Fixtures.java` (première version), `identity/MeIT`, `identity/TokenTamperingIT`, `identity/PasswordChangeRequiredIT`, `identity/OriginCheckFilterTest`, `identity/SecurityHeadersIT`, `identity/ClientIpIT`.

**Interfaces :**

- Produit :
  - **`JwtClaims`** : `EMAIL="email"`, `ROLE="role"`, `PERMS="perms"`, `PCR="pcr"`, `ISSUER="vps-sentinel"`.
  - **`record IssuedAccessToken(String value, long expiresInSeconds)`** ; `AccessTokenIssuer.issue(User user) : IssuedAccessToken`. Les claims viennent de la base via `PermissionMatrix`, et `iat`/`exp` du `Clock`.
  - **`record MeResponse(UUID id, String email, String fullName, String role, List<String> permissions, boolean mustChangePassword)`** ; `GET /api/v1/me`.
  - **`SecurityConfig`** :
    - pas de session, CSRF désactivé, aucune configuration CORS ;
    - `oauth2ResourceServer(jwt)`, avec un convertisseur d'autorités sans préfixe sur le claim `perms` ;
    - `permitAll` sur `POST /api/v1/auth/{login,refresh,logout}`, `GET /actuator/health`, `/v3/api-docs/**`. Tout le reste exige une authentification ;
    - `@EnableMethodSecurity`.
  - **`OriginCheckFilter`** : s'applique aux `POST /api/v1/auth/{login,refresh,logout}` ; `Origin` absent ou hors liste → 403 `origin-not-allowed`.
  - **`PasswordChangeRequiredFilter`** : si `pcr = true` et que la requête n'est pas `POST /api/v1/auth/change-password`, `POST /api/v1/auth/logout` ou `GET /api/v1/me` → 403 `password-change-required`.
  - **`Fixtures`** : `TestUser createUser(RoleCode role)`, avec un email unique et le mot de passe `Fixture-Password-1!` ; `String accessToken(TestUser u)` ; `String accessTokenWithPcr(TestUser u)` ; `String uniqueIp()` (`10.x.y.z` unique par appel) ; `String uniqueEmail()`. `record TestUser(UUID id, String email, String password, RoleCode role)`.

- [ ] **Étape 1 : écrire les tests.**

```java
// MeIT
@Test void meReturnsProfile() { /* DEVOPS → 200, $.role "DEVOPS", $.permissions contient "server:write" et pas "server:delete" */ }
@Test void meWithoutTokenIs401Problem() { /* 401, application/problem+json, $.type urn:sentinel:problem:unauthorized */ }
// TokenTamperingIT — chaque cas → 401 unauthorized
@Test void algNone(); @Test void signedWithOtherRsaKey(); @Test void hs256SignedWithPublicKeyBytes();
@Test void expired() { /* émission, puis clock.advance(16 min) → 401 (le validateur de dates utilise le Clock applicatif) */ }
@Test void wrongIssuer();
// PasswordChangeRequiredIT (contrôleur de test /test/secured avec @PreAuthorize("isAuthenticated()"))
@Test void pcrAllowsMe() { /* 200 */ } @Test void pcrBlocksOthers() { /* GET /test/secured → 403 password-change-required */ }
// OriginCheckFilterTest (MockHttpServletRequest)
@Test void listedOriginPasses(); @Test void missingOriginIs403(); @Test void foreignOriginIs403(); @Test void otherPathsIgnored();
// SecurityHeadersIT
@Test void apiResponsesAreNoStore() { /* GET /api/v1/me : Cache-Control contient "no-store" */ }
@Test void noCorsHeaders() { /* requête avec Origin: https://evil.example → aucun Access-Control-Allow-Origin */ }
// ClientIpIT
@Test void trustedProxyForwardedForIsUsed() { /* depuis 127.0.0.1 avec X-Forwarded-For 10.1.2.3 → RequestInfo.clientIp() == 10.1.2.3 (endpoint de test /test/ip) */ }
@Nested @TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=10\\.99\\.99\\.99")
class UntrustedProxy { @Test void forwardedForIgnored() { /* même requête → 127.0.0.1 */ } }
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter.**
  - Encodeur et décodeur `NimbusJwtEncoder` / `NimbusJwtDecoder.withPublicKey(...).signatureAlgorithm(RS256)`.
  - Validateurs : émetteur, plus `JwtTimestampValidator` branché sur `Clock` via `setClock`.
  - Point d'entrée et handler d'accès refusé délégués à `ProblemWriter`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(identity): rs256 access tokens and stateless security"`

## Tâche 10 — Login et limitation des tentatives

**Fichiers :**

- Créer :
  - dans `identity/internal/session/` : `LoginThrottleRepository.java`, `LoginThrottleService.java`, `RefreshTokenRepository.java` (insertion), `RefreshTokenService.java` (`startFamily`), `NewRefreshToken.java`, `RefreshCookies.java`, `AuthTokens.java`, `LoginService.java`, `LoginOutcome.java` ;
  - `identity/web/AuthController.java` (login), `identity/web/LoginRequest.java`, `identity/web/AuthResponse.java`.
- Modifier : `UserRepository` (ajout de `touchLastLogin`).
- Tests : `ThrottleDelayTest`, `LoginIT`, `LoginThrottleIT`, `LoginAuditIT`.

**Interfaces :**

- Produit :
  - **`LoginThrottleService`** :
    - `static Duration delayFor(int failures, int threshold)` ;
    - `Optional<Duration> blockedFor(String accountFp, String ipFp)` ;
    - `ThrottleResult recordFailure(String accountFp, String ipFp)`, avec `record ThrottleResult(int accountIpFailures, int ipFailures, Optional<Duration> startedBlock, String blockedScope)` ;
    - `void resetAccountIp(String accountFp, String ipFp)` ; `int clearAccount(String accountFp)`.
  - **`record NewRefreshToken(String rawValue, UUID id, UUID familyId, Instant expiresAt, Instant sessionStartedAt)`** ; `RefreshTokenService.startFamily(UUID userId, String ip, String userAgent) : NewRefreshToken`.
  - **`RefreshCookies`** :
    - `ResponseCookie issue(NewRefreshToken t)` : `Max-Age = min(24 h, sessionStartedAt + 7 j − now)` en secondes ;
    - `ResponseCookie clear()` : `Max-Age=0` ;
    - nom `sentinel_rt`, chemin `/api/v1/auth`.
  - **`record AuthTokens(IssuedAccessToken access, NewRefreshToken refresh, MeResponse user)`** ; **`record AuthResponse(String accessToken, String tokenType, long expiresIn, MeResponse user)`**, avec `tokenType="Bearer"`.
  - **`sealed interface LoginOutcome`** : `Success(AuthTokens tokens)`, `InvalidCredentials()`, `Throttled(Duration retryAfter)`.
  - **`LoginService.login(String identifier, String password, String ip, String userAgent) : LoginOutcome`** : `@Transactional`, jamais d'exception sur un échec.
  - **`UserRepository.touchLastLogin(UUID id, Instant at)`** : `UPDATE users SET last_login_at = :at WHERE id = :id`, sans toucher `version`.
  - **`record LoginRequest(@NotBlank @Size(max=254) String email, @NotBlank @Size(max=1024) String password)`**. La borne de 1024 limite le coût d'Argon2 ; la politique de 128 caractères ne s'applique qu'à la définition d'un mot de passe.

- [ ] **Étape 1 : écrire les tests.**

```java
// ThrottleDelayTest
@Test void delays() {
  assertThat(delayFor(4, 5)).isZero();
  assertThat(List.of(5,6,7,8,9,10,30).stream().map(n -> delayFor(n, 5).toSeconds()))
      .containsExactly(30L, 60L, 120L, 240L, 480L, 900L, 900L);
  assertThat(delayFor(20, 20)).hasSeconds(30);
}
// LoginIT (chaque requête : Origin http://localhost:5173, X-Forwarded-For unique)
@Test void successReturnsTokensAndCookie() {
  /* 200 ; $.tokenType "Bearer" ; $.expiresIn 900 ; Set-Cookie contient "sentinel_rt=", "HttpOnly", "Secure",
     "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=86400" */ }
@Test void wrongPasswordAndUnknownIdentifierAreIndistinguishable() { /* deux 401, même $.type et même $.detail "Identifiants invalides." */ }
@Test void disabledAccountRefusedWithoutIncrement() { /* bon mot de passe → 401 ; audit LOGIN_FAILED reason ACCOUNT_DISABLED ; aucune ligne login_throttles */ }
@Test void expiredTemporaryPasswordRefused() { /* password_expires_at = now − 1 s → 401, reason TEMP_PASSWORD_EXPIRED */ }
@Test void lastLoginUpdatedWithoutVersionBump() { /* version identique avant et après, last_login_at == clock.instant() */ }
@Test void missingOrForeignOriginIs403(); @Test void textPlainIs415();
// LoginThrottleIT (TestClock ; même compte et même IP sauf mention contraire)
@Test void fifthFailureBlocks30s() { /* 5 échecs → 401 ; 6e tentative immédiate → 429, Retry-After "30", detail "Trop de tentatives. Réessayez dans 30 secondes." */ }
@Test void progressiveDelaysCapAt15Min() { /* après chaque fin de blocage, un nouvel échec → 60, 120, 240, 480, 900, 900 */ }
@Test void neverPermanent() { /* au plafond, advance(15 min) → bon mot de passe → 200 */ }
@Test void otherIpUnaffected() { /* compte bloqué depuis l'IP A → depuis l'IP B, bon mot de passe → 200 */ }
@Test void ipScopeBlocksAfter20Failures() { /* 20 identifiants différents depuis la même IP → 21e tentative → 429 */ }
@Test void blockedAttemptsNeitherVerifyNorExtend() { /* bon mot de passe pendant le blocage → 429 ; à la fin du blocage → 200 */ }
@Test void successResetsAccountIpNotIpScope() { /* ownerJdbc : ligne ACCOUNT_IP supprimée ou à 0 ; ligne IP inchangée */ }
@Test void parallelFailuresCountedExactly() { /* 10 threads (CountDownLatch) → failure_count == 10 */ }
@Test void counterResetsAfterOneHour() { /* 3 échecs, advance(61 min), 1 échec → failure_count == 1 */ }
// LoginAuditIT
@ParameterizedTest @ValueSource(strings = {"S3cr3t-Passw0rd!", "p@ssw0rd-1234567"})
void rawIdentifierNeverStoredNorLogged(String typed, CapturedOutput output) {
  // login(typed, "x") → 401
  String all = ownerJdbc().sql("SELECT string_agg(a::text, '|') FROM audit_logs a WHERE correlation_id = :c").param("c", corr).query(String.class).single();
  assertThat(all).doesNotContain(typed).contains(fingerprints.identifier(typed));
  assertThat(output.getAll()).doesNotContain(typed);
}
@Test void knownAccountFailureTargetsUser() { /* target_type USER, target_id = id, actor_type ANONYMOUS, actor_email NULL */ }
@Test void throttleStartAuditedOnce() { /* une seule ligne LOGIN_THROTTLED : scope ACCOUNT_IP, failureCount 5, blockedSeconds 30 */ }
@Test void successAudited() { /* LOGIN_SUCCEEDED, actor_type USER */ }
@Test void ipv6ClientAddress() { /* X-Forwarded-For "2001:db8::1" → audit ip = '2001:db8::1'::inet ; la limitation suit cette IP */ }   // Review Focus 4
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter `LoginThrottleRepository.upsertFailure(scope, accountFp, ipFp, now)`** avec cette instruction, dans la transaction du login :

```sql
INSERT INTO login_throttles (id, scope, account_fp, ip_fp, failure_count, first_failure_at, last_failure_at)
VALUES (:id, :scope, :accountFp, :ipFp, 1, :now, :now)
ON CONFLICT (scope, coalesce(account_fp, ''), ip_fp) DO UPDATE SET
  failure_count    = CASE WHEN login_throttles.last_failure_at < :now - interval '1 hour' THEN 1 ELSE login_throttles.failure_count + 1 END,
  first_failure_at = CASE WHEN login_throttles.last_failure_at < :now - interval '1 hour' THEN :now ELSE login_throttles.first_failure_at END,
  last_failure_at  = :now
RETURNING failure_count
```

  Si le compteur atteint le seuil, `UPDATE … SET blocked_until = :now + delay`.
- [ ] **Étape 4 : implémenter `LoginService` selon le spec §8.4**, étapes 1 à 6.
  - Le hash factice est calculé une seule fois au démarrage.
  - L'audit `LOGIN_FAILED` porte `details.reason` et `details.identifierFingerprint`, avec l'acteur `AuditActor.anonymous()`.
  - Le contrôleur traduit `Throttled` en 429, avec `Retry-After` = secondes arrondies au supérieur.
- [ ] **Étape 5 : relancer.** Attendu : PASS.
- [ ] **Étape 6 : commit.** `git commit -m "feat(identity): login with progressive account+ip throttling"`

## Tâche 11 — Refresh, logout et purge des sessions

**Fichiers :**

- Modifier : `RefreshTokenRepository`, `RefreshTokenService`, `AuthController` (refresh, logout).
- Créer : `identity/internal/session/RefreshOutcome.java`, `identity/internal/session/SessionCleanupJob.java`, `identity/internal/session/RevokedReason.java`.
- Tests : `RefreshIT`, `LogoutIT`, `SessionCleanupJobIT`.

**Interfaces :**

- Produit :
  - **`enum RevokedReason`** `{ ROTATED, LOGOUT, REUSE_DETECTED, USER_DISABLED, ROLE_CHANGED, PASSWORD_CHANGED, PASSWORD_RESET }`.
  - **`sealed interface RefreshOutcome`** : `Rotated(AuthTokens tokens)`, `Rejected()`.
  - **`RefreshTokenService`** :
    - `RefreshOutcome rotate(String rawToken, String ip, String userAgent)` ;
    - `void logout(String rawToken)` ;
    - `int revokeAllForUser(UUID userId, RevokedReason reason)`.
  - **`SessionCleanupJob.purge()`** : `@Scheduled(cron = "0 30 3 * * *")`, avec `@EnableScheduling` sur une configuration d'`identity`.

- [ ] **Étape 1 : écrire les tests.**

```java
// RefreshIT (Origin autorisée, cookie extrait de la réponse au login)
@Test void rotates() { /* 200, nouvelle valeur de cookie ; ancienne ligne revoked_reason ROTATED, replaced_by = id de la nouvelle */ }
@Test void parallelRefreshExactlyOneSucceeds() { /* 8 threads, même cookie → un 200 et sept 401 ; ensuite, plus aucune ligne de la famille avec revoked_at NULL */ }
@Test void reuseRevokesFamilyEvenIfExpired() { /* rotation ; advance(25 h) ; ancien jeton → 401 ; famille en REUSE_DETECTED ; un seul TOKEN_REUSE_DETECTED avec secondsSinceRotation ≥ 90000, sameIp true */ }
@Test void reuseOnInactiveFamilyIsNotAudited() { /* logout, puis jeton tourné → 401 ; pas de nouvelle ligne TOKEN_REUSE_DETECTED */ }
@Test void idleExpiry() { /* advance(24 h + 1 s) → 401 */ }
@Test void absoluteExpiry() { /* refresh toutes les 23 h ; dernier Max-Age < 86400 ; après session_started_at + 7 j → 401 */ }
@Test void disabledUserRejectedWithoutSuccessor() { /* status DISABLED (ownerJdbc) → 401 ; jeton présenté en USER_DISABLED, replaced_by NULL ; aucune nouvelle ligne */ }
@Test void claimsRecomputedAfterRoleChange() { /* rôle changé en base → refresh → $.user.role et claim perms à jour */ }
@Test void originRequired() { /* sans Origin → 403 origin-not-allowed */ }
// LogoutIT
@Test void revokesFamilyAndClearsCookie() { /* 204 ; Set-Cookie sentinel_rt avec Max-Age=0 ; refresh ensuite → 401 ; audit LOGOUT */ }
@Test void noCookieIs204();
// SessionCleanupJobIT
@Test void purgesOldSessionsAndStaleThrottles() { /* session_started_at = now − 15 j → supprimée ; − 13 j → conservée ; throttle dont last_failure_at = now − 25 h, sans blocage → supprimé */ }
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter la rotation.**
  - Instruction : le bloc SQL du spec §8.2, avec `:now` à la place de `now()` (Global Constraints).
  - Si aucune ligne n'est modifiée, relire par `token_hash` et appliquer le spec §8.2, étape 4.
  - Révocation de famille : `UPDATE refresh_tokens SET revoked_at=:now, revoked_reason=:reason WHERE family_id=:f AND revoked_at IS NULL`.
  - `TOKEN_REUSE_DETECTED` n'est audité que si cette révocation a modifié au moins une ligne. `sameIp` et `sameUserAgent` comparent la requête au successeur (`replaced_by`).
  - Purge :
    - `DELETE FROM refresh_tokens WHERE session_started_at < :now - interval '14 days'` ;
    - `DELETE FROM login_throttles WHERE last_failure_at < :now - interval '24 hours' AND (blocked_until IS NULL OR blocked_until < :now)`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(identity): atomic refresh rotation, reuse detection, logout"`

## Tâche 12 — Changement de mot de passe et premier administrateur

**Fichiers :**

- Créer : `identity/internal/session/ChangePasswordService.java`, `identity/internal/session/ChangePasswordOutcome.java`, `identity/internal/admin/SuperAdminLock.java`, `identity/internal/bootstrap/BootstrapAdminRunner.java`, `identity/web/ChangePasswordRequest.java`.
- Modifier : `AuthController` (`POST /api/v1/auth/change-password`).
- Tests : `ChangePasswordIT`, `BootstrapAdminIT` (hérite d'`IsolatedDatabaseIT`).

**Interfaces :**

- Produit :
  - **`SuperAdminLock.acquire()`** : `SELECT pg_advisory_xact_lock(hashtext('sentinel.super_admin'))`. Il exige une transaction en cours (`MANDATORY`).
  - **`sealed interface ChangePasswordOutcome`** : `Changed(AuthTokens tokens)`, `WrongCurrentPassword()`, `Throttled(Duration retryAfter)`.
  - **`ChangePasswordService.change(UUID userId, String current, String next, String ip, String ua) : ChangePasswordOutcome`**.
  - **`record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword)`**.
  - **`BootstrapAdminRunner implements ApplicationRunner`** : sa méthode testable est `void bootstrap(String email, String password, String name)`.

- [ ] **Étape 1 : écrire les tests.**

```java
// ChangePasswordIT
@Test void successRevokesAllSessionsAndIssuesNewPair() {
  /* une 2e session (2e login) a son refresh → 401 après le changement ; must_change_password false ;
     password_expires_at NULL ; nouveau cookie émis ; audit PASSWORD_CHANGED */ }
@Test void wrongCurrentIs400OnField() { /* 400 validation-error, $.errors[0].field "currentPassword" ; audit PASSWORD_CHANGE_FAILED ; ligne ACCOUNT_IP failure_count 1 */ }
@Test void throttledIs429() { /* 5 mauvais mots de passe actuels → 6e appel 429 */ }
@Test void newPasswordPolicyIs400OnNewPassword(); @Test void sameAsCurrentRejected();
@Test void pcrUserCanChange() { /* jeton avec pcr=true → 200, et le nouveau jeton a pcr false */ }
// BootstrapAdminIT (base neuve pour la classe ; nettoyage des utilisateurs entre les tests via ownerJdbc)
@Test void createsWhenNoActiveSuperAdmin() { /* rôle SUPER_ADMIN, must_change_password true, password_expires_at NULL, audit USER_CREATED actor SYSTEM */ }
@Test void ignoredWhenActiveSuperAdminExists(); @Test void noopWhenVariablesMissing();
@Test void weakPasswordRefused() { /* 11 caractères → aucun utilisateur ; log d'erreur */ }
@Test void existingEmailNeverPromoted() { /* DEVELOPER existant avec cet email → rôle inchangé */ }
@Test void emailNormalized() { /* "  Admin@Sentinel.LOCAL " → stocké "admin@sentinel.local" ; POST /auth/login avec "admin@sentinel.local" → 200 */ }  // Review Focus 5
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter** selon le spec §8.5 et §8.7. Le contrôleur traduit `WrongCurrentPassword` en 400 avec `FieldViolation("currentPassword", "Mot de passe actuel incorrect.")`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(identity): password change and bootstrap admin"`

## Tâche 13 — Administration des utilisateurs

**Fichiers :**

- Créer :
  - dans `identity/internal/admin/` : `UserAdminService.java`, `PatchUserCommand.java`, `UserQueryService.java`, `IdentityConstraintMappings.java` ;
  - dans `identity/web/` : `UsersController.java`, `UserResponse.java`, `CreateUserRequest.java`, `CreatedUserResponse.java`, `TemporaryPasswordResponse.java`.
- Tests : `UsersIT`, `LastSuperAdminIT` (hérite d'`IsolatedDatabaseIT`), `PatchUserCommandTest`.

**Interfaces :**

- Consomme : `SuperAdminLock.acquire()`, `RefreshTokenService.revokeAllForUser`, `LoginThrottleService.clearAccount`, `Fingerprints.identifier`, `TemporaryPasswordGenerator`, `PasswordPolicy`.
- Produit :
  - **Endpoints** du spec §10.1 pour `/api/v1/users` (`@PreAuthorize` avec `Permissions.USER_READ` ou `USER_MANAGE`). `SortSpec` : `fullName`, `email`, `createdAt`, `lastLoginAt` ; tri par défaut `fullName,asc`.
  - **`PatchUserCommand.parse(ObjectNode body) : PatchUserCommand`** (Jackson 3, `tools.jackson.databind.node.ObjectNode`). Elle distingue un champ absent (inchangé) d'un champ `null` explicite (400 sur ce champ). Champ inconnu → 400. `version` est obligatoire.
  - **`record CreatedUserResponse(UserResponse user, String temporaryPassword, Instant temporaryPasswordExpiresAt)`** ; **`record TemporaryPasswordResponse(String temporaryPassword, Instant temporaryPasswordExpiresAt)`**.
  - **Bean `ConstraintMapping("uq_users_email_active", DUPLICATE, "email")`**.

- [ ] **Étape 1 : écrire les tests.**

```java
// PatchUserCommandTest
@Test void absentFieldsUnchanged(); @Test void explicitNullRejected(); @Test void unknownFieldRejected(); @Test void versionRequired();
// UsersIT (SUPER_ADMIN sauf mention contraire)
@Test void createReturnsTemporaryPasswordOnce() { /* 201 ; temporaryPassword de 16 caractères ; expiresAt == now + 72 h ; GET /users/{id} sans champ temporaryPassword */ }
@Test void duplicateEmailIs409OnEmail(); @Test void emailNormalizedOnCreate();
@Test void listFiltersAndSorts() { /* q, role, status ; sort=lastLoginAt,desc ; sort=passwordHash → 400 champ sort */ }
@Test void patchStaleVersionIs409();
@Test void roleChangeRevokesSessionsAndAudits() { /* refresh de l'utilisateur → 401 ; USER_ROLE_CHANGED avec changes.role {from, to} */ }
@Test void disableRevokesAndEnableAudited() { /* USER_DISABLED puis USER_ENABLED */ }
@Test void selfRoleChangeDisableDeleteAre409() { /* type self-modification-forbidden */ }
@Test void resetPasswordRevokesClearsThrottlesAndExpires() {
  /* lignes ACCOUNT_IP du compte présentes avant, absentes après ; details.throttlesCleared ≥ 1 ;
     ancien mot de passe → 401 ; mot de passe temporaire → 200 avec mustChangePassword true */ }
@Test void deletedUserCannotLoginAndEmailReusable();
@Test void infraAdminReadsButCannotManage() { /* GET 200, POST 403 */ }
// LastSuperAdminIT
@Test void lastSuperAdminCannotBeDemotedDisabledOrDeleted() { /* 409 last-super-admin */ }
@Test void concurrentMutualDemotionKeepsOne() {
  /* 20 itérations : SUPER_ADMIN A et B ; en parallèle A rétrograde B et B rétrograde A →
     count(SUPER_ADMIN actifs) ≥ 1 ; une des deux requêtes a reçu 409 last-super-admin */ }
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter.**
  - Toute opération qui réduit le nombre de SUPER_ADMIN appelle `SuperAdminLock.acquire()`, puis compte les SUPER_ADMIN actifs autres que la cible.
  - La réinitialisation calcule `Fingerprints.identifier(user.email)` pour appeler `clearAccount`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(identity): user administration with guardrails"`

## Tâche 14 — VPS (module infrastructure)

**Fichiers :**

- Créer :
  - `V5__servers.sql` ;
  - API publique de `infrastructure/` : `AdminStatus.java`, `ServerSummary.java`, `ServerCatalog.java`, `ServerUsageChecker.java` ;
  - dans `infrastructure/internal/` : `Server.java`, `OsFamily.java`, `ServerRepository.java`, `TagRules.java`, `ServerService.java`, `JdbcServerCatalog.java`, `ServersController.java`, `ServerRequest.java`, `ServerResponse.java`, `ServerFacetsResponse.java`, `InfrastructureConstraintMappings.java`.
- Tests : `TagRulesTest`, `ServersIT`.

**Interfaces :**

- Produit :
  - `enum AdminStatus { ACTIVE, MAINTENANCE, DECOMMISSIONED }` ; `record ServerSummary(UUID id, String name, AdminStatus adminStatus)`.
  - **`ServerCatalog`** :
    - `ServerSummary lockForDeployment(UUID serverId)` : `SELECT id, name, admin_status, deleted_at FROM servers WHERE id = :id FOR SHARE` ; 404 s'il est absent, 409 `server-not-deployable` s'il est supprimé ou `DECOMMISSIONED` ;
    - `Map<UUID, ServerSummary> summaries(Collection<UUID> ids)`.
  - **`ServerUsageChecker.hasDeployments(UUID serverId) : boolean`**. `ServerService` reçoit une `List<ServerUsageChecker>` ; une liste vide vaut « aucun déploiement ».
  - **`TagRules.normalize(List<String> raw) : List<String>`** : suppression des espaces, minuscules, dédoublonnage, tri ; motif `^[a-z0-9][a-z0-9._-]*$` ; 1 à 50 caractères ; 20 au plus. Violation → `ApiException` sur le champ `tags`.
  - **`record ServerRequest(...)`** : champs du spec §9.3 plus `List<String> tags` et `Long version`. `version` est obligatoire pour `PUT` (groupe de validation `OnUpdate`) et doit être absent pour `POST`.
  - **`Server.auditState()`** : tous les champs du spec §9.3 sauf les dates, plus `tags`.
  - **`SortSpec`** : `name`, `adminStatus`, `provider`, `region`, `createdAt` ; tri par défaut `name,asc`.
  - **Bean `ConstraintMapping("uq_servers_name_active", DUPLICATE, "name")`**.

- [ ] **Étape 1 : écrire `V5__servers.sql`.**
  - Tables `servers` et `server_tags` (`@ElementCollection`) du spec §9.3, avec les contraintes `CHECK`.
  - `CREATE UNIQUE INDEX uq_servers_name_active ON servers (lower(name)) WHERE deleted_at IS NULL;`
- [ ] **Étape 2 : écrire les tests.**

```java
// TagRulesTest
@Test void normalizes() { assertThat(TagRules.normalize(List.of(" Prod ", "prod", "WEB"))).containsExactly("prod", "web"); }
@Test void rejectsSpaceInTag(); @Test void rejects21Tags(); @Test void rejects51Chars();
// ServersIT
@Test void createAndGet() { /* 201, puis GET : champs égaux, nom débarrassé des espaces, tags normalisés, hostname en minuscules */ }
@Test void duplicateNameCaseAndSpaceInsensitive() { /* "VPS-01" puis "  vps-01 " → 409 duplicate, $.errors[0].field "name" */ }   // Review Focus 1
@Test void putReplacesAndClearsOptional() { /* PUT sans hostname → hostname null ; version + 1 */ }
@Test void putStaleVersionIs409(); @Test void putUnknownIs404();
@Test void pageBeyondLastIsEmpty() { /* page=999 → items [], totalItems exact */ }
@Test void filters() { /* q sur le hostname, q sur une IP exacte, tag, provider, region, adminStatus */ }
@Test void unknownSortIs400();
@Test void facetsDistinctSortedExcludingDeleted();
@Test void deleteSoftDeletesAndFreesName() { /* GET → 404 ; recréation avec le même nom → 201 */ }
@ParameterizedTest @MethodSource("invalidPayloads") void validationTable(String field, Object value) { /* 400 avec $.errors[*].field contenant ce champ — cas du spec §10.3 */ }
@Test void permissions() { /* VIEWER POST 403 ; DEVELOPER PUT 403 ; DEVOPS DELETE 403 ; INFRA_ADMIN DELETE 204 */ }
@Test void auditDiffAndSnapshot() { /* SERVER_UPDATED : seuls les champs modifiés dans changes ; SERVER_DELETED : instantané */ }
```

- [ ] **Étape 3 : lancer les tests.** Attendu : échec.
- [ ] **Étape 4 : implémenter.**
  - `q` filtre `name`/`hostname` par `ILIKE` ; si `q` est une IP valide, il ajoute `host(public_ip) = q OR host(private_ip) = q`.
  - `PUT` vers `DECOMMISSIONED` et `DELETE` : charger le VPS avec `PESSIMISTIC_WRITE`, interroger les `ServerUsageChecker`, puis 409 `server-in-use` si l'un d'eux répond vrai (spec §7.2).
  - Les dates viennent du `Clock`.
- [ ] **Étape 5 : relancer.** Attendu : PASS.
- [ ] **Étape 6 : commit.** `git commit -m "feat(infrastructure): servers inventory with full-replace updates"`

## Tâche 15 — Projets et environnements

**Fichiers :**

- Créer :
  - `V6__projects.sql` ;
  - dans `projects/internal/` : `Project.java`, `Environment.java`, `EnvironmentKind.java`, `ProjectRepository.java`, `EnvironmentRepository.java`, `ProjectService.java`, `EnvironmentService.java`, `ProjectsController.java`, `EnvironmentsController.java`, `ProjectRequest.java`, `ProjectResponse.java`, `ProjectDetailResponse.java`, `EnvironmentRequest.java`, `EnvironmentResponse.java`, `ProjectsConstraintMappings.java`.
- Tests : `ProjectsIT`, `EnvironmentsIT`.

**Interfaces :**

- Produit :
  - **Endpoints** du spec §10.1 pour `/api/v1/projects` et `/api/v1/environments`.
  - **`ProjectResponse`** inclut `environmentCount`. **`ProjectDetailResponse`** inclut `List<EnvironmentDetail>` ; `domains` et `deployments` restent vides jusqu'à la tâche 16.
  - **`EnvironmentService.softDeleteWithChildren(Environment env)`** : point d'extension que la tâche 16 complète.
  - **`SortSpec`** : `name`, `createdAt` ; tri par défaut `name,asc`.
  - **Beans `ConstraintMapping`** : `uq_projects_name_active` et `uq_environments_project_name_active` vers `DUPLICATE` sur le champ `name`.
  - **Liste blanche d'audit** : `Project` (`name`, `description`, `repositoryUrl`), `Environment` (`projectId`, `name`, `kind`).

- [ ] **Étape 1 : écrire `V6__projects.sql`** (spec §9.4).

```sql
CREATE UNIQUE INDEX uq_projects_name_active ON projects (lower(name)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_environments_project_name_active ON environments (project_id, lower(name)) WHERE deleted_at IS NULL;
```

- [ ] **Étape 2 : écrire les tests.**

```java
// ProjectsIT
@Test void createGetListWithEnvironmentCount();
@Test void duplicateNameIs409();
@Test void repositoryUrlRules() { /* "javascript:alert(1)" et "https://u:p@h/x" → 400 champ repositoryUrl */ }
@Test void putReplacesAndStaleVersionIs409();
@Test void deleteSoftDeletesProjectAndEnvironmentsWithSnapshots() { /* GET → 404 ; PROJECT_DELETED et ENVIRONMENT_DELETED avec instantanés */ }
// EnvironmentsIT
@Test void nameUniquePerProjectCaseInsensitive() { /* "prod" puis "PROD" dans le même projet → 409 ; "prod" dans un autre projet → 201 */ }
@Test void environmentNameRules() { /* "pré prod" et "-prod" → 400 champ name ; nom de 51 caractères → 400 ; kind inconnu → 400 champ kind */ }
@Test void putAndDelete();
@Test void putOnEnvironmentOfDeletedProjectIs404() { /* projet supprimé → PUT /environments/{id} → 404 not-found */ }   // Review Focus 3
@Test void permissions() { /* DEVELOPER POST 201 et DELETE 403 ; VIEWER POST 403 */ }
```

- [ ] **Étape 3 : lancer les tests.** Attendu : échec.
- [ ] **Étape 4 : implémenter.** Un environnement dont le projet est supprimé est traité comme introuvable : la recherche passe par un projet non supprimé.
- [ ] **Étape 5 : relancer.** Attendu : PASS.
- [ ] **Étape 6 : commit.** `git commit -m "feat(projects): projects and environments"`

## Tâche 16 — Domaines, déploiements et cohérence avec les VPS

**Fichiers :**

- Créer :
  - `V7__domains_deployments.sql` ;
  - dans `projects/internal/` : `Domain.java`, `Deployment.java`, `DomainService.java`, `DeploymentService.java`, `DomainsController.java`, `DeploymentsController.java`, `ServerDeploymentsController.java`, `ProjectDeploymentUsage.java`.
- Modifier : `EnvironmentService.softDeleteWithChildren`, `ProjectDetailResponse`, `ProjectsConstraintMappings`.
- Tests : `DomainsIT`, `DeploymentsIT`.

**Interfaces :**

- Consomme : `ServerCatalog.lockForDeployment`, `ServerCatalog.summaries`.
- Produit :
  - `POST /api/v1/environments/{id}/domains`, `DELETE /api/v1/domains/{id}`, `POST /api/v1/environments/{id}/deployments`, `DELETE /api/v1/deployments/{id}`, `GET /api/v1/servers/{id}/deployments`, qui renvoie `List<HostedEnvironmentResponse(UUID deploymentId, UUID projectId, String projectName, UUID environmentId, String environmentName, String role)>`.
  - `ProjectDeploymentUsage implements ServerUsageChecker`.
  - Beans `ConstraintMapping` : `uq_domains_hostname` vers `DUPLICATE` sur `hostname` ; `uq_deployments_env_server` vers `DEPLOYMENT_EXISTS`.

- [ ] **Étape 1 : écrire `V7__domains_deployments.sql`** (spec §9.4).

```sql
CREATE UNIQUE INDEX uq_domains_hostname ON domains (hostname);
CREATE UNIQUE INDEX uq_domains_primary ON domains (environment_id) WHERE is_primary;
ALTER TABLE deployments ADD CONSTRAINT uq_deployments_env_server UNIQUE (environment_id, server_id);
CREATE INDEX ix_deployments_server ON deployments (server_id);
```

- [ ] **Étape 2 : écrire les tests.**

```java
// DomainsIT
@Test void hostnameNormalizedAndGloballyUnique();
@Test void primarySwitchKeepsSingle() { /* a (principal), puis b (principal) → a.is_primary false, b true */ }
@Test void invalidHostnames() { /* "demo.example.com.", "*.example.com", "bücher.example", "example" → 400 */ }
// DeploymentsIT
@Test void attachAndListOnServer() { /* GET /servers/{id}/deployments → nom du projet, nom de l'environnement, rôle */ }
@Test void duplicateAttachIs409DeploymentExists();
@Test void deploymentRoleRules() { /* role absent → 201 ; "web worker" → 201 ; "-web" et 51 caractères → 400 champ role */ }
@Test void decommissionedOrDeletedServerNotDeployable() { /* 409 server-not-deployable ; VPS inconnu → 404 */ }
@Test void deleteOrDecommissionServerInUseIs409() { /* DELETE /servers/{id} et PUT adminStatus DECOMMISSIONED → 409 server-in-use */ }
@Test void detachThenDeleteServer() { /* 204 */ }
@Test void concurrentAttachAndDeleteNeverOrphans() {
  /* 30 itérations : nouveau VPS ; en parallèle POST deployments et DELETE server →
     jamais (servers.deleted_at NOT NULL et un déploiement existe pour ce VPS) */ }
@Test void deletingEnvironmentRemovesChildrenWithSnapshots() { /* DOMAIN_REMOVED.details.hostname ; DEPLOYMENT_REMOVED.details.serverId */ }
@Test void projectDetailIncludesDomainsAndDeploymentsWithServerStatus();
```

- [ ] **Étape 3 : lancer les tests.** Attendu : échec.
- [ ] **Étape 4 : implémenter.**
  - `DeploymentService.attach` appelle `lockForDeployment` avant l'insertion, dans la même transaction (spec §7.2).
  - Bascule du domaine principal : `UPDATE domains SET is_primary = false WHERE environment_id = :e AND is_primary`, puis insertion.
- [ ] **Étape 5 : relancer.** Attendu : PASS.
- [ ] **Étape 6 : commit.** `git commit -m "feat(projects): domains, deployments, server consistency"`

## Tâche 17 — Lecture de l'audit, matrice d'autorisation complète, contrat OpenAPI

**Fichiers :**

- Créer : `audit/internal/AuditQueryService.java`, `audit/AuditLogsController.java`, `audit/AuditLogResponse.java`, `shared/web/OpenApiConfig.java`, `scripts/export-openapi.sh`, `contracts/openapi/core-api.yaml`.
- Tests : `AuditLogsIT`, `AuthorizationMatrixIT`, `DenyByDefaultIT`, `OpenApiContractIT`, `ProdProfileIT`.

**Interfaces :**

- Produit :
  - `GET /api/v1/audit-logs` : filtres `actorUserId`, `action`, `targetType`, `targetId`, `from`, `to` ; tri `occurredAt` seulement, `desc` par défaut.
  - `OpenApiConfig` : titre « VPS Sentinel Core API », version « 1 », schéma de sécurité `bearer` (JWT).
  - Test `record EndpointCase(HttpMethod method, String path, String access, boolean pcrExempt)`, où `access` vaut une constante `Permissions`, `"PUBLIC"` ou `"AUTHENTICATED"`. Une entrée par ligne du spec §10.1.

- [ ] **Étape 1 : écrire les tests.**

```java
// AuditLogsIT
@Test void filtersAndNewestFirst(); @Test void developerForbidden(); @Test void infraAdminAllowed();
// AuthorizationMatrixIT
@ParameterizedTest @MethodSource("casesTimesRoles")
void accessFollowsMatrix(EndpointCase c, RoleCode role) {
  // ids aléatoires, corps minimal ; autorisé ⇒ statut ∉ {401, 403} ; refusé ⇒ 403 urn:sentinel:problem:forbidden
}
@ParameterizedTest @MethodSource("nonExemptCases") void pcrBlocks(EndpointCase c) { /* jeton pcr → 403 password-change-required */ }
@ParameterizedTest @MethodSource("protectedCases") void noTokenIs401(EndpointCase c);
// DenyByDefaultIT
@Test void everyMappingHasAMatrixEntry() {
  // paires (méthode, motif) de RequestMappingHandlerMapping, hors /error et /v3/api-docs**
  assertThat(registeredMappings).containsExactlyInAnyOrderElementsOf(AuthorizationMatrixIT.CASES_AS_KEYS);
}
// OpenApiContractIT
@Test void contractUpToDate() {
  // GET /v3/api-docs.yaml ; si UPDATE_OPENAPI=true, écrire ../../contracts/openapi/core-api.yaml ; sinon assertEquals(fichier, réponse)
}
// ProdProfileIT (@ActiveProfiles("prod"), dossier de secrets de test)
@Test void apiDocsDisabled() { /* GET /v3/api-docs → 404 */ }
```

- [ ] **Étape 2 : lancer les tests.** Attendu : échec.
- [ ] **Étape 3 : implémenter** le contrôleur, le service, la configuration OpenAPI et `scripts/export-openapi.sh` (`cd services/core-api && UPDATE_OPENAPI=true ./mvnw -q test -Dtest=OpenApiContractIT`).
- [ ] **Étape 4 : générer le contrat, puis relancer.**

  ```bash
  bash scripts/export-openapi.sh
  ./mvnw verify
  ```

  Attendu : `BUILD SUCCESS`, et `contracts/openapi/core-api.yaml` créé.
- [ ] **Étape 5 : commit.** `git update-index --chmod=+x scripts/export-openapi.sh`, puis `git commit -m "feat: audit read api, authorization matrix tests, openapi contract"`.

## Tâche 18 — Squelette frontend, thème, mise en page, client typé

**Fichiers :**

- Créer : `frontend/` (template `react-ts`), `package.json` (versions des Global Constraints), `vite.config.ts`, `eslint.config.js`, `vitest.config.ts`, `src/test/setup.ts`, `src/test/server.ts`, `src/api/schema.d.ts` (généré), `src/api/client.ts`, `src/api/problem.ts`, `src/auth/permissions.ts`, `src/theme.ts`, `src/main.tsx`, `src/router.tsx`, `src/layout/AppLayout.tsx`, `frontend/.dockerignore`.
- Tests : `src/layout/AppLayout.test.tsx`, `src/api/problem.test.ts`.

**Interfaces :**

- Produit :
  - **Scripts npm :** `dev`, `build` (`tsc -b && vite build`), `lint` (`eslint .`), `typecheck` (`tsc -b --noEmit`), `test` (`vitest run`), `gen:api` (`openapi-typescript ../contracts/openapi/core-api.yaml -o src/api/schema.d.ts`).
  - **`createApiClient(middleware: Middleware[]) : Client<paths>`** (openapi-fetch, `baseUrl: ''`).
  - **`problem.ts`** : `type Problem = { type: string; title?: string; status: number; detail?: string; correlationId?: string; errors?: { field: string; message: string }[] }` ; `isProblem(x: unknown): x is Problem` ; `problemCode(p: Problem): string` (suffixe de l'URN) ; `fieldErrors(p: Problem): Record<string, string>`.
  - **`can(user: Me | null, permission: string): boolean`**.
  - **`vite.config.ts`** : proxy `/api` vers `http://localhost:8080` ; Vitest avec `environment: 'jsdom'` et `setupFiles: ['src/test/setup.ts']`.
  - **`eslint.config.js`** : `typescript-eslint` recommandé, `react-hooks`, et `react/no-danger: 'error'`.

- [ ] **Étape 1 : générer et installer.**

  ```bash
  npm create vite@9.3.3 frontend -- --template react-ts
  ```

  Fixer les versions exactes dans `package.json`, puis `npm install` et `npm run gen:api`.
- [ ] **Étape 2 : écrire les tests.**

```tsx
test('hides Users and Audit for a DEVELOPER', () => {
  renderWithUser(<AppLayout />, { permissions: ['server:read', 'project:read', 'project:write'] });
  expect(screen.queryByRole('link', { name: 'Utilisateurs' })).toBeNull();
  expect(screen.queryByRole('link', { name: 'Audit' })).toBeNull();
  expect(screen.getByRole('link', { name: 'VPS' })).toBeInTheDocument();
});
test('shows Users and Audit for INFRA_ADMIN', () => { /* user:read, audit:read */ });
test('fieldErrors maps errors[]', () => {
  expect(fieldErrors({ type: 'urn:sentinel:problem:validation-error', status: 400, errors: [{ field: 'name', message: 'requis' }] })).toEqual({ name: 'requis' });
});
test('isProblem rejects plain JSON', () => expect(isProblem({ foo: 1 })).toBe(false));
```

- [ ] **Étape 3 : lancer `npx vitest run src/layout src/api`.** Attendu : échec.
- [ ] **Étape 4 : implémenter** `AppLayout` (`AppShell`, navigation Tableau de bord, VPS, Projets, Utilisateurs, Audit, filtrée par `can`, menu utilisateur, bascule clair/sombre), `problem.ts`, `client.ts`, et le routeur avec des pages provisoires.
- [ ] **Étape 5 : relancer, puis vérifier l'ensemble.** `npm run lint && npm run typecheck && npm test`. Attendu : vert.
- [ ] **Étape 6 : vérifier la règle `react/no-danger`.** Créer `src/tmp.tsx` contenant `dangerouslySetInnerHTML`, lancer `npm run lint` (attendu : erreur `react/no-danger`), puis supprimer le fichier.
- [ ] **Étape 7 : commit.** `git commit -m "feat(frontend): skeleton, layout, typed api client"`

## Tâche 19 — Authentification côté client

**Fichiers :**

- Créer : `src/auth/session.ts`, `src/auth/refreshCoordinator.ts`, `src/auth/authChannel.ts`, `src/api/authMiddleware.ts`, `src/auth/AuthProvider.tsx`, `src/auth/guards.tsx`, `src/auth/LoginPage.tsx`, `src/auth/ChangePasswordPage.tsx`, `src/auth/ProfilePage.tsx`.
- Tests : `refreshCoordinator.test.ts`, `authMiddleware.test.ts`, `authChannel.test.ts`, `LoginPage.test.tsx`, `guards.test.tsx`, `ChangePasswordPage.test.tsx`.

**Interfaces :**

- Produit :
  - **`session.ts`** : `getAccessToken(): string | null`, `getUser(): Me | null`, `setSession(r: AuthResponse): void`, `clearSession(): void`, `subscribe(cb): () => void`.
  - **`refreshCoordinator.ts`** : `refreshOnce(doRefresh: () => Promise<AuthResponse | null>): Promise<AuthResponse | null>`.
    - Une seule promesse en cours par onglet.
    - Elle s'exécute sous `navigator.locks.request('sentinel-refresh', { signal }, …)`, avec un `AbortController` qui expire après 10 000 ms.
    - Sans `navigator.locks`, elle s'exécute directement.
  - **`authChannel.ts`** : `broadcastLogout(): void`, `onLogout(cb: () => void): () => void` (`BroadcastChannel('sentinel-auth')`).
  - **`authMiddleware.ts`** : `authMiddleware(opts: { refresh: () => Promise<AuthResponse | null>; onSessionLost: () => void; onPasswordChangeRequired: () => void }): Middleware`.
    - Il ajoute `Authorization: Bearer`.
    - Sur un 401, il appelle `refreshOnce` puis rejoue la requête une fois.
    - Chemins exclus : `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`.
    - Sur un 403 `password-change-required`, il appelle `onPasswordChangeRequired`.
  - **`AuthProvider`** expose `useAuth(): { user, login(email, password), logout(), changePassword(current, next) }`.
  - **`guards.tsx`** : `<RequireAuth>`, qui redirige vers `/login`, puis vers `/change-password` si `mustChangePassword` ; `<RequirePermission permission="…">`.

- [ ] **Étape 1 : écrire les tests.**

```ts
test('concurrent callers share one refresh', async () => { /* 3 appels → doRefresh appelé 1 fois */ });
test('uses navigator.locks when available', async () => { /* locks.request appelé avec 'sentinel-refresh' */ });
test('falls back when navigator.locks is undefined', async () => { /* aucune exception ; doRefresh appelé 1 fois */ });
test('gives up after 10 s waiting for the lock', async () => { /* faux minuteurs : advanceTimersByTime(10_000) → null */ });
test('retries once after refresh on 401', async () => { /* MSW : 401 puis 200 ; requête rejouée avec le nouveau jeton */ });
test('three simultaneous 401s trigger a single refresh', async () => {});
test('login 401 does not trigger refresh', async () => {});
test('change-password 401 does trigger refresh', async () => {});
test('session lost when refresh fails', async () => { /* onSessionLost appelé */ });
test('logout in one tab clears the other', async () => { /* deux AuthProvider ; logout() sur A → B vide sa session */ });
test('login shows generic message on 401', async () => { /* "Identifiants invalides." */ });
test('login shows countdown on 429', async () => { /* Retry-After: 30 → "Réessayez dans 30 secondes" */ });
test('login submit disabled while pending', async () => {});
test('redirects to /change-password when mustChangePassword', () => {});
test('maps 400 currentPassword error to the field', async () => {});
```

- [ ] **Étape 2 : lancer `npx vitest run src/auth src/api/authMiddleware.test.ts`.** Attendu : échec.
- [ ] **Étape 3 : implémenter.** Au montage, `AuthProvider` appelle `POST /api/v1/auth/refresh` via `refreshOnce`. `logout()` appelle l'API, puis `clearSession()` et `broadcastLogout()`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(frontend): in-memory session, coordinated refresh, auth pages"`

## Tâche 20 — Écrans VPS

**Fichiers :**

- Créer : `src/components/PaginatedTable.tsx`, `src/components/useListParams.ts`, `src/components/ConfirmDelete.tsx`, `src/api/applyProblem.ts`, et dans `src/features/servers/` : `api.ts`, `ServersListPage.tsx`, `ServerDetailPage.tsx`, `ServerFormPage.tsx`.
- Tests : `src/features/servers/servers.test.tsx`.

**Interfaces :**

- Produit :
  - **`useListParams(defaults: { sort: string }) : { params: ListParams; setParams(p: Partial<ListParams>): void }`**, synchronisé avec l'URL (`page`, `size`, `sort`, `q`, filtres).
  - **`applyProblem(p: Problem, form?: UseFormReturnType<any>): void`** :
    - erreurs de champ via `form.setErrors` ;
    - `version-conflict` → notification « Cet élément a été modifié par quelqu'un d'autre. Rechargez la page. » ;
    - `server-in-use` → « VPS utilisé par des déploiements » ;
    - sinon, notification avec `detail`.
  - **Hooks dans `api.ts`** : `useServers(params)`, `useServer(id)`, `useServerFacets()`, `useServerDeployments(id)`, `useCreateServer()`, `useUpdateServer(id)` (envoie l'objet complet en `PUT`, avec `version` ; champs vides → `null`), `useDeleteServer()`.

- [ ] **Étape 1 : écrire les tests.**

```tsx
test('list reads filters from the URL', async () => { /* /servers?q=web&page=2 → la requête MSW reçoit q=web&page=2 */ });
test('changing sort updates the URL', async () => {});
test('edit sends a full PUT with cleared fields as null and the version', async () => {});
test('server field errors are shown on inputs', async () => {});
test('version conflict shows the exact message', async () => {});
test('submit is disabled while saving', async () => { /* bouton désactivé tant que la mutation est en cours */ });   // Review Focus 2
test('delete of a server in use shows "VPS utilisé par des déploiements"', async () => {});
test('delete asks for confirmation before calling the API', async () => { /* aucun appel DELETE avant le clic sur « Confirmer » ; « Annuler » → aucun appel */ });
test('viewer sees no create, edit or delete actions', () => {});
test('detail lists hosted environments', async () => {});
```

- [ ] **Étape 2 : lancer `npx vitest run src/features/servers`.** Attendu : échec.
- [ ] **Étape 3 : implémenter** les pages et les composants partagés. Le champ de tags, de fournisseur et de région s'appuie sur `useServerFacets`.
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(frontend): servers screens"`

## Tâche 21 — Écrans projets, environnements, domaines, déploiements

**Fichiers :**

- Créer, dans `src/features/projects/` : `api.ts`, `ProjectsListPage.tsx`, `ProjectDetailPage.tsx`, `EnvironmentModal.tsx`, `DomainModal.tsx`, `DeploymentModal.tsx`.
- Tests : `src/features/projects/projects.test.tsx`.

**Interfaces :**

- Consomme : `useListParams`, `applyProblem`, `PaginatedTable`, `ConfirmDelete`, `useServers` (sélection du VPS dans `DeploymentModal`).
- Produit : `useProjects`, `useProject(id)`, `useCreateProject`, `useUpdateProject(id)`, `useDeleteProject`, `useCreateEnvironment(projectId)`, `useUpdateEnvironment(id)`, `useDeleteEnvironment`, `useAddDomain(envId)`, `useRemoveDomain`, `useAttachServer(envId)`, `useDetachDeployment`.

- [ ] **Étape 1 : écrire les tests.**

```tsx
test('renders one tab per environment', async () => {});
test('adding a primary domain marks it as primary', async () => {});
test('deployment-exists error is shown in the modal', async () => {});
test('developer can add an environment but has no delete action', () => {});
test('repository link opens with rel="noopener noreferrer"', async () => {});
test('submit is disabled while saving in modals', async () => {});
```

- [ ] **Étape 2 : lancer `npx vitest run src/features/projects`.** Attendu : échec.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer.** Attendu : PASS.
- [ ] **Étape 5 : commit.** `git commit -m "feat(frontend): projects, environments, domains, deployments"`

## Tâche 22 — Utilisateurs, audit, tableau de bord

**Fichiers :**

- Créer : dans `src/features/users/` : `api.ts`, `UsersPage.tsx`, `UserFormModal.tsx`, `TemporaryPasswordModal.tsx` ; dans `src/features/audit/` : `api.ts`, `AuditPage.tsx` ; `src/features/dashboard/DashboardPage.tsx`.
- Tests : `users.test.tsx`, `audit.test.tsx`, `dashboard.test.tsx`.

**Interfaces :**

- Produit :
  - **`usePatchUser(id)`** : n'envoie que les champs modifiés, plus `version`.
  - **`useResetPassword(id)`**.
  - **`TemporaryPasswordModal`** : props `{ password: string; expiresAt: string; onClose(): void }`. `onClose` appelle `mutation.reset()`.
  - **`DashboardPage`** :
    - 3 requêtes `GET /servers?adminStatus=<statut>&size=1` et `GET /projects?size=1`, dont il lit `totalItems` ;
    - `GET /audit-logs?size=10` seulement si `can(user, 'audit:read')`.

- [ ] **Étape 1 : écrire les tests.**

```tsx
test('temporary password is shown once with its expiry and dropped on close', async () => {
  /* affiché avec un bouton Copier et la date d'expiration ; après fermeture, absent du DOM et le résultat de mutation est vidé */ });
test('patch sends only changed fields and version', async () => { /* corps == { role: 'DEVOPS', version: 3 } */ });
test('audit filters are sent as query params', async () => {});
test('dashboard counters equal totalItems', async () => {});
test('dashboard never calls audit-logs without audit:read', async () => { /* nombre d'appels du handler MSW == 0 */ });
```

- [ ] **Étape 2 : lancer `npx vitest run src/features/users src/features/audit src/features/dashboard`.** Attendu : échec.
- [ ] **Étape 3 : implémenter.**
- [ ] **Étape 4 : relancer, puis vérifier l'ensemble.** `npm run lint && npm run typecheck && npm test && npm run build`. Attendu : vert.
- [ ] **Étape 5 : commit.** `git commit -m "feat(frontend): users, audit and dashboard screens"`

## Tâche 23 — Conteneurs frontend et gateway, fichiers Compose complets

**Fichiers :**

- Créer : `frontend/Dockerfile`, `frontend/nginx/spa.conf`, `infrastructure/gateway/nginx.dev.conf`, `infrastructure/gateway/nginx.prod.conf`, `infrastructure/gateway/security-headers.conf`, `infrastructure/gateway/proxy-headers.conf`, `docker-compose.prod.yml`, `scripts/check-compose-ports.sh`.
- Modifier : `docker-compose.yml` (ajout de `frontend` et `gateway`), `docker-compose.dev.yml` (gateway).

**Interfaces :**

- Produit : services `frontend` (réseau `app`) et `gateway` (réseaux `public` et `app`). Le script `check-compose-ports.sh` est réutilisé par la CI.

- [ ] **Étape 1 : écrire `scripts/check-compose-ports.sh`.**
  - Il produit la configuration dev et la configuration prod avec `docker compose -f docker-compose.yml -f docker-compose.<env>.yml config --format json`. Pour la prod, il fournit des valeurs factices à `SENTINEL_TLS_CERT_DIR`, `SENTINEL_ACME_WEBROOT` et `SENTINEL_SECRETS_DIR`.
  - Il extrait les ports publiés avec `node -e`.
  - Attendus :
    - dev : `{gateway: [8088], postgres: [5432]}` ;
    - prod : `{gateway: [80, 443]}` ;
    - aucun autre service ne publie de port.
- [ ] **Étape 2 : lancer `bash scripts/check-compose-ports.sh`.** Attendu : échec, ni gateway ni configuration prod.
- [ ] **Étape 3 : `frontend/Dockerfile` et `spa.conf`.**
  - Dockerfile : build sur `node:24-alpine` (`npm ci`, `npm run build`) ; exécution sur `nginx:1.30.6-alpine`.
  - `spa.conf` : `try_files $uri /index.html;`, `server_tokens off;`.
- [ ] **Étape 4 : configuration du gateway.**
  - **`security-headers.conf`** : exactement les en-têtes du spec §6.4, avec `always`.
  - **`proxy-headers.conf`** :

    ```nginx
    proxy_set_header X-Request-Id $request_id;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Forwarded "";
    ```

  - **`nginx.dev.conf` et `nginx.prod.conf`** :
    - `limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;` et `zone=auth:10m rate=30r/m;` ;
    - `limit_req_status 429;` ;
    - `location = /api/v1/auth/login { limit_req zone=login burst=5 nodelay; … }` ;
    - `location /api/v1/auth/ { limit_req zone=auth burst=10 nodelay; … }` ;
    - `location /api/` ; `location = /actuator/health` ; `location /` vers `frontend` ;
    - chaque `location` inclut `security-headers.conf` et `proxy-headers.conf` ;
    - `error_page 429 = @rate_limited;` avec :

      ```nginx
      location @rate_limited {
        default_type application/problem+json;
        include security-headers.conf;
        return 429 '{"type":"urn:sentinel:problem:rate-limited","title":"Trop de requêtes","status":429}';
      }
      ```

    - `server_tokens off;`, `client_max_body_size 1m;`, `log_format` avec `$request_id`.
  - **Spécifique à `nginx.prod.conf`** :
    - serveur sur le port 80 : `location /.well-known/acme-challenge/ { root /var/www/acme; }` et `return 301 https://$host$request_uri;` ;
    - serveur 443 `ssl` : `/etc/nginx/certs/fullchain.pem`, `privkey.pem`, `ssl_protocols TLSv1.2 TLSv1.3;`, en-tête HSTS.
- [ ] **Étape 5 : Compose.**
  - Base : `frontend` et `gateway`, ce dernier dépendant de `core-api` (`service_healthy`) et de `frontend`.
  - Dev : gateway sur `8088:80` avec `nginx.dev.conf`.
  - Prod : gateway sur `80:80` et `443:443`, `nginx.prod.conf`, certificats et webroot ACME, secrets lus dans `${SENTINEL_SECRETS_DIR}`.
- [ ] **Étape 6 : vérifier.**
  - `bash scripts/check-compose-ports.sh` → `OK` ;
  - `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build`, puis :
    - `curl -sI http://localhost:8088/` → `Content-Security-Policy` présent ;
    - `curl -s http://localhost:8088/actuator/health` → `{"status":"UP"}` ;
    - `curl -s -o /dev/null -w '%{http_code}' http://localhost:8088/api/v1/me` → `401` ;
    - `curl -s http://localhost:8088/v3/api-docs | head -c 15` → pas de JSON OpenAPI.
  - Puis `down -v`.
- [ ] **Étape 7 : commit.** `git update-index --chmod=+x scripts/check-compose-ports.sh`, puis `git commit -m "feat(infra): frontend and gateway containers, prod compose overlay"`.

## Tâche 24 — Tests E2E Playwright

**Fichiers :**

- Créer : `tests/e2e/package.json`, `tests/e2e/playwright.config.ts`, `tests/e2e/helpers/api.ts`, `tests/e2e/journeys.spec.ts`, `tests/e2e/gateway.spec.ts`, `scripts/e2e.sh`.

**Interfaces :**

- Produit :
  - **`helpers/api.ts`** : `apiLogin(request, email, password): Promise<string>` (envoie `Origin: http://localhost:8088`) ; `ADMIN_EMAIL` et `ADMIN_BOOTSTRAP_PASSWORD`, lus dans l'environnement avec pour défauts les valeurs de `.env.example` ; `ADMIN_NEW_PASSWORD = 'Admin-E2E-Password-1!'`.
  - **`scripts/e2e.sh`** : `down -v` ; génération des secrets s'ils manquent ; `up -d --build` ; attente de l'état `healthy` du gateway ; `npx playwright test` ; `down -v` en sortie (`trap`).

- [ ] **Étape 1 : écrire `playwright.config.ts`.** `baseURL: 'http://localhost:8088'`, Chromium seul, `workers: 1`, projets `journeys` puis `gateway`, avec `dependencies: ['journeys']`.
- [ ] **Étape 2 : écrire les scénarios.**

```ts
// journeys.spec.ts — test.describe.configure({ mode: 'serial' })
test('E2E-1 admin journey', async ({ page, request }) => {
  // 1-2 connexion avec l'admin de bootstrap → /change-password → ADMIN_NEW_PASSWORD
  // 3 VPS-01 (fournisseur, région, OS, tags) ; 4 projet Demo, environnement prod (PRODUCTION), domaine demo.example.com
  // 5 rattachement prod ↔ VPS-01 (rôle "web") ; 6 fiche VPS-01 contient "Demo" et "prod" ;
  //   /audit contient SERVER_CREATED, PROJECT_CREATED, DEPLOYMENT_ADDED avec IP et correlationId non vides
  // 7 suppression de VPS-01, puis passage en DECOMMISSIONED → message "VPS utilisé par des déploiements" les deux fois
});
test('E2E-2 viewer is read-only', async ({ page, request }) => {
  // l'admin crée un VIEWER et lit le mot de passe temporaire ; connexion du VIEWER → changement forcé
  // VPS et projets visibles ; aucun bouton Créer, Modifier ou Supprimer ; liens Utilisateurs et Audit absents
  // POST /api/v1/servers avec son jeton → 403
});
// gateway.spec.ts
test('security headers on / and /api', async ({ request }) => {});
test('spoofed X-Forwarded-For is not the audited ip', async ({ request }) => {
  // login échoué avec X-Forwarded-For: 203.0.113.77 ; jeton admin → GET /api/v1/audit-logs?action=LOGIN_FAILED&sort=occurredAt,desc
  // l'ip de l'entrée la plus récente est différente de 203.0.113.77
});
test('rate limit answers 429 problem json', async ({ request }) => {
  // 20 POST /api/v1/auth/login rapides → au moins une réponse 429 avec content-type application/problem+json
});
```

- [ ] **Étape 3 : lancer `bash scripts/e2e.sh`.** Attendu : échec possible au premier passage (sélecteurs). Ajuster les tests aux libellés réels de l'interface, sans jamais affaiblir une assertion.
- [ ] **Étape 4 : relancer jusqu'au vert.** Attendu : `5 passed`.
- [ ] **Étape 5 : commit.** `git update-index --chmod=+x scripts/e2e.sh`, puis `git commit -m "test(e2e): admin and viewer journeys, gateway checks"`.

## Tâche 25 — CI, README, documentation de déploiement, vérification finale

**Fichiers :**

- Créer : `.github/workflows/ci.yml`, `docs/deployment/production-prerequisites.md`.
- Modifier : `README.md`.

**Interfaces :**

- Consomme : `scripts/check-repo.sh`, `scripts/check-compose-ports.sh`, `scripts/e2e.sh`, `scripts/gen-dev-secrets.sh`.

- [ ] **Étape 1 : écrire `ci.yml`**, déclenché sur `push` et `pull_request`.
  - **`checks`** : `bash scripts/check-repo.sh`, `bash scripts/check-compose-ports.sh`.
  - **`backend`** : `actions/setup-java` (`temurin`, `25`), puis `cd services/core-api && ./mvnw -B verify`.
  - **`frontend`** : `actions/setup-node` (`24`), `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, puis `npm run gen:api && git diff --exit-code src/api/schema.d.ts`.
  - **`e2e`** (`needs: [checks, backend, frontend]`) : `cp .env.example .env`, `bash scripts/gen-dev-secrets.sh`, `cd tests/e2e && npm ci && npx playwright install --with-deps chromium`, `bash scripts/e2e.sh`.
- [ ] **Étape 2 : README.**
  - Prérequis : JDK 25, Node 24, Docker 29 ou plus.
  - Commandes du spec §6.7, avec l'équivalent PowerShell `.\mvnw.cmd`.
  - Avertissement : phase 1 non autorisée en production (spec §15).
  - Retrait des variables de bootstrap après la première connexion.
  - Règles git du spec §5.2.
- [ ] **Étape 3 : `docs/deployment/production-prerequisites.md`.**
  - La liste du spec §15 sous forme de cases à cocher.
  - La procédure de changement des mots de passe des rôles : `ALTER ROLE … PASSWORD` avec le superutilisateur, mise à jour de `.env`, redémarrage de `migrate` et `core-api`.
  - Un brouillon de la procédure de renouvellement TLS : certbot en mode webroot sur `${SENTINEL_ACME_WEBROOT}`, puis `docker compose … exec gateway nginx -s reload`.
- [ ] **Étape 4 : vérification finale**, critères du spec §14.1, depuis la racine :

```bash
bash scripts/check-repo.sh                                        # critère 1
(cd services/core-api && ./mvnw -B verify)                        # critère 6
(cd frontend && npm run lint && npm run typecheck && npm test && npm run build)
bash scripts/check-compose-ports.sh                               # critère 3
bash scripts/e2e.sh                                               # critères 2, 4, 5, 7
```

  Attendu : tout vert. Consigner la sortie de chaque commande dans le message de fin de tâche. Le critère 8 se vérifie en relisant le README.
- [ ] **Étape 5 : commit.** `git commit -m "ci: pipeline, readme and production prerequisites"`

---

## Couverture de la spec

| Spec | Tâches |
|---|---|
| §5.1–5.3 dépôt, isolation, hygiène | 0, 1 |
| §6.1–6.3 services, réseaux, Compose | 4, 23 |
| §6.4 gateway | 23, 24 |
| §6.5 rôles et migrations | 3, 7, 8 |
| §6.6 configuration et secrets | 2, 4, 6 |
| §6.7 workflow | 25 |
| §7 modules, concurrence, transactions | 2, 7, 13, 14, 16 |
| §8.1 access token | 9 |
| §8.2–8.3 refresh et concurrence | 10, 11 |
| §8.4 login et limitation | 10 |
| §8.5 logout et changement de mot de passe | 11, 12 |
| §8.6 mots de passe | 8, 13 |
| §8.7 bootstrap | 12 |
| §8.8 garde-fous | 13 |
| §8.9 CSRF, CORS, XSS | 9, 18, 21 |
| §8.10 IP réelle | 9, 10, 23, 24 |
| §8.11 Spring Security | 9, 17 |
| §8.12 matrice | 8, 17 |
| §9 modèle de données | 7, 8, 14, 15, 16 |
| §10 API, validation | 5, 6, 13–17 |
| §11 erreurs et logs | 5, 2 |
| §12 frontend | 18–22 |
| §13 tests | toutes ; §13.9 : 1 et 23 ; §13.10 : 25 |
| §14 critères | 25 |
| §15 conditions de production | 25 (documentation) |
