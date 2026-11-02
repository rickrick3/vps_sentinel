# VPS SENTINEL
## Technical Architecture Document (TAD)

**Version :** 1.0  
**Statut :** Architecture de référence proposée  
**Type de produit :** Plateforme interne de supervision d'infrastructure  
**Architecture :** Hybride, évolutive vers des microservices  
**Périmètre :** Une organisation, plusieurs VPS et projets

---

# 1. Présentation du projet

## 1.1 Vision

VPS Sentinel est une plateforme centralisée de supervision, d'analyse et de gestion des infrastructures informatiques de l'entreprise.

Elle doit permettre de surveiller plusieurs serveurs VPS, leurs ressources système, les services qui y fonctionnent et les projets qui y sont hébergés.

La plateforme doit détecter les dysfonctionnements, prévenir les interruptions lorsque cela est possible, alerter les équipes et conserver un historique exploitable des incidents.

VPS Sentinel ne doit pas être limité à la détection de disponibilité. Il doit progressivement devenir un outil d'observabilité et d'aide à la décision pour l'exploitation de l'infrastructure.

## 1.2 Objectifs principaux

- Centraliser l'inventaire des VPS et des projets.
- Surveiller la disponibilité des serveurs et des services.
- Collecter et historiser les métriques système.
- Détecter les interruptions et comportements anormaux.
- Gérer les incidents et leur cycle de vie.
- Envoyer des notifications email.
- Identifier les risques de saturation et les dégradations progressives.
- Conserver une traçabilité des événements et des actions.
- Introduire progressivement des mécanismes de remédiation contrôlée.

## 1.3 Périmètre fonctionnel initial

La première version couvre :

1. Authentification et gestion des utilisateurs.
2. Gestion des VPS.
3. Gestion des projets et environnements.
4. Enregistrement des agents.
5. Collecte des métriques système.
6. Surveillance des services Linux et Docker.
7. Vérifications HTTP et TCP.
8. Détection des incidents.
9. Notifications email.
10. Historique et tableau de bord.

Les analyses prédictives avancées et la remédiation automatisée seront développées ultérieurement.

---

# 2. Décisions architecturales

| Domaine | Décision |
|---|---|
| Frontend | React + TypeScript |
| Backend principal | Java + Spring Boot |
| Agent de surveillance | Go |
| Intelligence et prédiction | Python + FastAPI |
| Base relationnelle | PostgreSQL |
| Stockage métriques | VictoriaMetrics |
| Message broker | RabbitMQ |
| Cache | Redis |
| Déploiement | Docker Compose |
| Authentification utilisateur | JWT + Spring Security |
| Périmètre organisationnel | Entreprise unique |
| Communication agent | HTTPS sortant uniquement |
| Remédiation | Approbation humaine obligatoire |

## 2.1 Architecture hybride

Le projet adopte une architecture hybride.

Les domaines fonctionnels seront séparés logiquement et pourront être déployés indépendamment lorsque cela sera pertinent.

Au démarrage, deux applications Spring Boot principales seront déployées :

- Core API.
- Monitoring Service.

FastAPI sera introduit lorsque les besoins d'analyse avancée seront suffisamment définis.

Le Go Agent sera un programme indépendant installé sur chaque VPS.

Cette organisation évite une multiplication prématurée des microservices tout en préparant leur séparation future.

---

# 3. Architecture logique

## 3.1 Presentation Layer

Technologie : React + TypeScript.

Responsabilités :

- Authentification.
- Dashboard global.
- Inventaire des VPS.
- Gestion des projets.
- Visualisation des métriques.
- État des services.
- Gestion des incidents.
- Gestion des alertes.
- Historique et rapports.
- Gestion des utilisateurs.

L'interface communique avec le backend via HTTPS/REST.

Une communication WebSocket ou Server-Sent Events pourra être ajoutée pour les actualisations en temps réel.

## 3.2 API Gateway

Responsabilités :

- Point d'entrée HTTPS.
- Routage des requêtes.
- Terminaison TLS.
- Limitation du débit.
- Gestion des en-têtes de sécurité.
- Routage vers les services applicatifs.

La validation des permissions métier reste effectuée par les services Spring Boot.

## 3.3 Core API — Spring Boot

Le Core API constitue le centre de gestion de VPS Sentinel.

Modules :

### Identity
- Utilisateurs.
- Rôles.
- Permissions.
- Sessions et révocation.
- Authentification JWT.

### Infrastructure
- VPS.
- Fournisseurs.
- Régions.
- Systèmes d'exploitation.
- Caractéristiques matérielles.
- Statut administratif.

### Agents
- Enregistrement.
- Identité des agents.
- Version installée.
- Révocation.
- Rotation des credentials.
- Historique de connexion.

### Projects
- Projets.
- Environnements.
- Domaines.
- URLs.
- Déploiements.
- Associations avec les VPS.

### Service Registry
- Nginx.
- Gunicorn.
- Django.
- Node.js.
- MySQL/MariaDB.
- Redis.
- Docker.
- Autres services configurables.

### Configuration
- Profils de surveillance.
- Intervalles de collecte.
- Seuils.
- Règles de vérification.
- Paramètres de notification.

### Audit
- Actions administratives.
- Modifications de configuration.
- Approbations.
- Rejets.
- Historique des changements.

## 3.4 Monitoring Service — Spring Boot

Responsabilités :

- Réception des données des agents.
- Gestion des heartbeats.
- Traitement des métriques.
- Contrôles de disponibilité.
- Surveillance des services.
- Détection des incidents.
- Évaluation des règles d'alertes.
- Publication des événements RabbitMQ.
- Coordination des notifications.

Il doit être conçu pour traiter les événements de façon idempotente.

## 3.5 Intelligence Service — FastAPI

Service indépendant prévu pour une phase ultérieure.

Responsabilités :

- Analyse des tendances.
- Détection statistique des anomalies.
- Estimation de saturation des ressources.
- Calcul des scores de risque.
- Analyse des historiques d'incidents.
- Génération de recommandations.

Les résultats de ce service seront des recommandations et des événements de risque. Il ne pourra pas exécuter directement des commandes sur les VPS.

## 3.6 VPS Agent — Go

Agent léger installé sur chaque VPS.

Responsabilités :

- Collecte CPU.
- Collecte RAM.
- Collecte disque.
- Collecte réseau.
- Surveillance Systemd.
- Surveillance Docker.
- Vérifications de processus.
- Heartbeat.
- Transmission des événements.
- Mise en attente temporaire des données si le serveur central est indisponible.

L'agent fonctionnera comme service Linux géré par Systemd.

---

# 4. Architecture de communication

## 4.1 Communication synchrone

Protocole : HTTPS / REST.

Utilisation :

- Frontend vers Core API.
- Frontend vers Monitoring API.
- Agent vers API d'ingestion.
- Services vers services pour les opérations nécessitant une réponse immédiate.
- Monitoring externe vers endpoints de santé.

## 4.2 Communication asynchrone

Technologie : RabbitMQ.

Le message broker assure la distribution des événements entre les composants.

Événements principaux :

| Routing key | Description |
|---|---|
| agent.heartbeat.received | Heartbeat reçu |
| agent.offline.detected | Agent hors ligne |
| metrics.received | Métriques reçues |
| service.status.changed | Changement d'état |
| incident.created | Incident créé |
| incident.updated | Incident mis à jour |
| incident.resolved | Incident résolu |
| alert.triggered | Alerte déclenchée |
| notification.requested | Notification demandée |
| risk.detected | Risque détecté |

## 4.3 Contrat d'événement

Tous les événements partagés doivent utiliser une enveloppe standard.

Exemple :

```json
{
  "eventId": "uuid",
  "eventType": "service.status.changed",
  "schemaVersion": 1,
  "occurredAt": "2026-11-02T10:30:00Z",
  "producer": "monitoring-service",
  "correlationId": "uuid",
  "payload": {
    "serverId": "uuid",
    "serviceId": "uuid",
    "previousStatus": "UP",
    "currentStatus": "DOWN"
  }
}
```

Règles :

- `eventId` est unique.
- Les dates sont en UTC.
- Les consommateurs dédupliquent les événements.
- Les événements sont versionnés.
- Les messages échoués sont traités avec retry et Dead Letter Queue.
- Les événements métier importants sont publiés via un mécanisme Transactional Outbox afin d'éviter les incohérences entre PostgreSQL et RabbitMQ.

## 4.4 Exchanges et queues

Exchanges proposés :

- `sentinel.events` : événements métier.
- `sentinel.commands` : demandes de traitement interne.
- `sentinel.notifications` : demandes de notification.

Les queues seront définies par consommateur et par responsabilité.

Les messages doivent être persistants pour les événements importants, avec acknowledgements explicites et politiques de retry contrôlées.

---

# 5. Architecture de données

## 5.1 PostgreSQL

PostgreSQL conserve les données métier.

Tables principales :

### Identité et sécurité
- users
- roles
- permissions
- user_roles
- refresh_tokens
- audit_logs

### Infrastructure
- servers
- agents
- agent_credentials
- server_groups
- server_tags

### Projets
- projects
- environments
- deployments
- project_servers
- domains

### Surveillance
- monitored_services
- service_checks
- check_results
- monitoring_profiles
- metric_definitions

### Incidents
- incidents
- incident_events
- incident_assignments
- incident_acknowledgements

### Alertes
- alert_rules
- alert_conditions
- alert_channels
- notification_templates
- notification_deliveries

### Exploitation
- maintenance_windows
- remediation_requests
- remediation_approvals
- remediation_executions

Les identifiants principaux seront des UUID.

Les suppressions logiques seront privilégiées pour les entités nécessitant un historique d'audit.

## 5.2 VictoriaMetrics

Stocke les métriques temporelles.

Exemples :

- `sentinel_cpu_usage_percent`
- `sentinel_memory_usage_percent`
- `sentinel_disk_usage_percent`
- `sentinel_network_receive_bytes_total`
- `sentinel_network_transmit_bytes_total`
- `sentinel_http_response_duration_seconds`
- `sentinel_service_up`

Les métriques auront des labels maîtrisés : server_id, service_id, environment, éventuellement project_id.

Éviter les labels à forte cardinalité comme les identifiants de requêtes ou les messages d'erreur.

Les métriques brutes ne seront pas stockées dans PostgreSQL.

## 5.3 Redis

Utilisation :

- Cache de configuration.
- Cache des informations fréquemment consultées.
- Verrous temporaires.
- États transitoires.

Redis ne sera pas considéré comme une source de vérité.

## 5.4 Rétention

La politique de rétention sera configurable selon les besoins et le volume.

Proposition initiale à valider après mesure :

- Métriques détaillées : 30 jours.
- Agrégations : 6 à 12 mois.
- Incidents et audits : conservation longue selon la politique de l'entreprise.

---

# 6. Modèle de surveillance

## 6.1 Trois niveaux de santé

La plateforme distinguera :

1. Process health : processus présent.
2. Service health : service répondant correctement.
3. Application health : application accessible et dépendances fonctionnelles.

Un service actif ne sera jamais considéré automatiquement comme une application saine.

## 6.2 Types de checks

- Process check.
- Systemd check.
- Docker container check.
- HTTP check.
- TCP port check.
- TLS certificate check.
- Application health endpoint.
- Dépendances DB et Redis.

## 6.3 Surveillance externe

Les agents locaux ne suffisent pas à confirmer l'accessibilité publique.

Des sondes externes indépendantes vérifieront :

- DNS.
- Connectivité réseau.
- HTTPS.
- Certificats.
- Latence.
- Disponibilité publique des applications.

## 6.4 Heartbeat

Configuration initiale proposée :

- Intervalle : 30 secondes.
- Délai de grâce : 60 secondes.
- Détection de l'absence de heartbeat par le backend.
- Événement de rétablissement après reconnexion.

Le statut d'un agent sera calculé côté serveur à partir de la dernière réception valide.

Les horodatages de l'agent et du serveur seront conservés séparément.

---

# 7. Gestion des incidents

## 7.1 Cycle de vie

Statuts :

- OPEN.
- ACKNOWLEDGED.
- RESOLVED.
- CLOSED.

Une panne persistante doit mettre à jour l'incident existant au lieu d'en créer un nouveau à chaque contrôle.

## 7.2 Sévérités

- INFO.
- WARNING.
- CRITICAL.

Les règles devront permettre de définir des seuils, des durées de confirmation et des fenêtres de maintenance.

## 7.3 Déduplication

Une clé de regroupement sera calculée à partir de la ressource concernée, du type de problème et du contexte.

Une même panne persistante doit être représentée par un incident unique.

## 7.4 Notifications

Canal initial : email SMTP.

Notifications :

- Nouvel incident.
- Incident critique.
- Rappel d'incident non acquitté.
- Incident résolu.
- Résumé périodique.

Le moteur devra gérer les tentatives d'envoi, les erreurs SMTP et les doublons.

---

# 8. Sécurité

## 8.1 Authentification utilisateur

Technologie : Spring Security + JWT.

- Access tokens de courte durée.
- Refresh tokens rotatifs.
- Révocation des refresh tokens.
- Hash des mots de passe avec Argon2id ou BCrypt.
- Contrôle des rôles et permissions côté backend.
- Protection contre les attaques par force brute.
- Journalisation des connexions importantes.

Les JWT ne doivent pas être utilisés comme unique mécanisme de révocation de session.

## 8.2 Rôles

Rôles initiaux :

- SUPER_ADMIN.
- INFRA_ADMIN.
- DEVOPS.
- DEVELOPER.
- VIEWER.

Les permissions seront définies explicitement, avec le principe du moindre privilège.

## 8.3 Authentification des agents

L'identité des agents sera indépendante de celle des utilisateurs.

Chaque agent disposera de credentials propres, révocables et renouvelables.

Les échanges seront protégés par HTTPS. mTLS pourra être introduit pour renforcer l'identification mutuelle.

## 8.4 Permissions Linux

L'agent utilisera un compte système dédié.

- Pas de root permanent.
- Accès en lecture aux informations nécessaires.
- Permissions minimales pour les checks Systemd.
- Accès Docker limité et documenté.
- Aucun accès arbitraire à un shell distant.

## 8.5 Remédiation

Aucune action corrective automatique ne sera activée par défaut.

Une action devra suivre le processus :

1. Détection.
2. Proposition.
3. Création d'une demande.
4. Approbation par une personne autorisée.
5. Vérification de la validité de l'approbation.
6. Exécution d'une action autorisée et prédéfinie.
7. Vérification du résultat.
8. Journalisation complète.

Les actions seront limitées à une liste blanche. Aucun mécanisme d'exécution de commandes arbitraires ne sera exposé.

---

# 9. Déploiement

## 9.1 Control Plane

Le Control Plane sera installé sur un VPS dédié.

Services Docker :

- Frontend.
- Gateway / Reverse Proxy.
- Core API.
- Monitoring Service.
- PostgreSQL.
- RabbitMQ.
- VictoriaMetrics.
- Redis.

FastAPI sera ajouté ultérieurement.

## 9.2 Agents

Les agents Go fonctionneront nativement sur les VPS surveillés, sous Systemd.

La communication sera sortante uniquement.

Aucune connexion SSH entrante depuis le Control Plane ne sera nécessaire.

## 9.3 Réseaux

- Réseau public : HTTPS uniquement.
- Réseau applicatif privé.
- Réseau de données privé.
- RabbitMQ non exposé publiquement.
- Redis non exposé publiquement.
- PostgreSQL non exposé publiquement.

## 9.4 Sauvegardes

- Sauvegardes PostgreSQL régulières.
- Sauvegarde des configurations.
- Sauvegarde et test de restauration des données nécessaires.
- Volumes persistants pour les composants stateful.
- Politique de rétention.
- Procédure documentée de reprise après incident.

## 9.5 Haute disponibilité

La première version ne garantit pas la haute disponibilité du Control Plane.

L'architecture devra cependant permettre une évolution vers plusieurs instances applicatives, une base PostgreSQL redondée et des mécanismes de reprise.

Un watchdog externe indépendant devra surveiller le Control Plane et son endpoint public.

---

# 10. Observabilité de VPS Sentinel

VPS Sentinel doit également se surveiller lui-même.

Indicateurs :

- Disponibilité de l'API.
- Latence des endpoints.
- Erreurs HTTP.
- Profondeur des queues RabbitMQ.
- Messages en Dead Letter Queue.
- Temps de traitement des événements.
- Connexions PostgreSQL.
- Utilisation CPU/RAM des conteneurs.
- Erreurs de notification.
- Retards d'ingestion des métriques.

Les logs devront être structurés et contenir des identifiants de corrélation.

---

# 11. Exigences non fonctionnelles

## Fiabilité
- Détection des interruptions.
- Traitement idempotent.
- Persistance des événements importants.
- Retry et Dead Letter Queue.
- Reprise après indisponibilité temporaire du Control Plane.

## Performance
- Collecte métrique configurable.
- Traitement asynchrone.
- Pagination des listes.
- Agrégation des données pour les dashboards.
- Contrôle de la cardinalité des métriques.

## Sécurité
- HTTPS.
- Authentification indépendante des agents et utilisateurs.
- Gestion des secrets.
- Audit des actions sensibles.
- Permissions minimales.

## Maintenabilité
- Contrats API documentés.
- Contrats d'événements versionnés.
- Tests automatisés.
- Migrations SQL.
- Logs structurés.
- Documentation de déploiement.

---

# 12. Structure du monorepo

```text
VPS-SENTINEL/
├── frontend/
├── services/
│   ├── core-api/
│   ├── monitoring-service/
│   └── intelligence-service/
├── agent/
│   └── go-agent/
├── infrastructure/
│   ├── gateway/
│   ├── postgres/
│   ├── rabbitmq/
│   ├── redis/
│   ├── victoriametrics/
│   └── docker/
├── contracts/
│   ├── openapi/
│   ├── events/
│   └── schemas/
├── docs/
│   ├── architecture/
│   ├── security/
│   ├── deployment/
│   └── operations/
├── tests/
├── scripts/
├── docker-compose.yml
└── README.md
```

---

# 13. Roadmap de développement

## Phase 0 — Architecture et spécifications

Livrables :
- TAD.
- Diagrammes d'architecture.
- Modèle de données.
- Contrats API.
- Contrats RabbitMQ.
- Modèle de sécurité.
- Stratégie de tests.

Critère de validation : les frontières des composants et les contrats sont approuvés.

## Phase 1 — Foundation

Développement :
- Initialisation du monorepo.
- Core API Spring Boot.
- Frontend React.
- PostgreSQL.
- Authentification JWT.
- Utilisateurs et rôles.
- Gestion des VPS.
- Gestion des projets et environnements.

Critère de validation : un utilisateur autorisé peut enregistrer un VPS et associer des projets.

## Phase 2 — Agent Go

Développement :
- Agent Linux.
- Configuration locale.
- Enregistrement sécurisé.
- Heartbeat.
- CPU, RAM, disque et réseau.
- Collecteur Systemd.
- Collecteur Docker.
- Transmission HTTPS.
- Queue locale temporaire.

Critère de validation : un VPS réel transmet régulièrement ses données au Control Plane.

## Phase 3 — Monitoring Engine

Développement :
- API d'ingestion.
- VictoriaMetrics.
- Health checks.
- Surveillance des services.
- Détection agent offline.
- Surveillance HTTP et TCP.
- Historique des états.

Critère de validation : VPS Sentinel détecte correctement une panne simulée de Nginx et distingue processus actif et application indisponible.

## Phase 4 — Incidents et RabbitMQ

Développement :
- Exchanges et queues.
- Événements versionnés.
- Consommateurs.
- Déduplication.
- Gestion des incidents.
- Retry et DLQ.
- Transactional Outbox.

Critère de validation : une panne confirmée génère un seul incident et son rétablissement clôture correctement le cycle.

## Phase 5 — Notifications

Développement :
- Configuration SMTP.
- Templates email.
- Alert rules.
- Déduplication.
- Rappels.
- Notifications de rétablissement.

Critère de validation : une panne Nginx provoque une notification email et son rétablissement une seconde notification.

## Phase 6 — Dashboard et reporting

Développement :
- Vue globale.
- Détails VPS.
- Détails projets.
- Graphiques historiques.
- Liste des incidents.
- Filtres.
- Rapports périodiques.

Critère de validation : l'équipe peut identifier rapidement les VPS et projets affectés.

## Phase 7 — Intelligence

Développement :
- FastAPI.
- Agrégation historique.
- Détection d'anomalies.
- Tendances.
- Prévisions de saturation.
- Scores de risque.
- Recommandations.

Critère de validation : le système identifie une tendance anormale sur des données de test et présente une recommandation explicable.

## Phase 8 — Remédiation contrôlée

Développement :
- Catalogue d'actions autorisées.
- Demandes de remédiation.
- Approbations.
- Exécution limitée.
- Vérification après action.
- Audit complet.

Critère de validation : une action ne peut être exécutée sans approbation valide et toutes ses étapes sont traçables.

---

# 14. Scénario de validation MVP

Le scénario prioritaire sera :

1. Créer un compte administrateur.
2. Enregistrer VPS-01.
3. Générer et installer son agent.
4. Vérifier le heartbeat.
5. Visualiser CPU, RAM, disque et réseau.
6. Enregistrer Nginx comme service surveillé.
7. Configurer un HTTP health check.
8. Arrêter Nginx sur le VPS.
9. Détecter la dégradation.
10. Créer un incident.
11. Envoyer un email.
12. Redémarrer Nginx manuellement.
13. Vérifier le rétablissement.
14. Résoudre l'incident.
15. Envoyer l'email de récupération.

Le MVP sera considéré fonctionnel lorsque ce parcours aura été validé sur un VPS réel.

---

# 15. Principes directeurs

1. La sécurité prime sur la facilité d'administration.
2. Aucun agent ne doit pouvoir exécuter des commandes arbitraires.
3. Les données historiques doivent être conservées selon une politique claire.
4. Un incident doit être traçable de sa détection à sa résolution.
5. Les services doivent avoir des responsabilités non redondantes.
6. Les événements doivent être versionnés et idempotents.
7. Les prédictions doivent être explicables et basées sur des données.
8. La plateforme doit être testable et observable.
9. Les microservices seront séparés selon des besoins réels, pas pour multiplier les technologies.
10. Le système de monitoring doit être surveillé indépendamment.

---

**Conclusion**

VPS Sentinel est conçu comme une plateforme interne d'observabilité et de gestion opérationnelle des infrastructures.

L'architecture Java + Go + Python, associée à RabbitMQ, PostgreSQL, VictoriaMetrics et Docker, permet de concilier performance, évolutivité et spécialisation technologique.

La priorité est de livrer un premier parcours de supervision fiable sur un VPS réel avant d'introduire les fonctionnalités avancées.
