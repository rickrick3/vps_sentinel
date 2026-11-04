# Conditions préalables à la mise en production

> **La phase 1 n'est pas autorisée en production** (spec §15). Chaque point ci-dessous doit être fait **et vérifié**, avec la date et la personne qui l'a vérifié, avant tout déploiement. Une spec dédiée les détaillera.

## Liste de contrôle

- [ ] **Sauvegardes PostgreSQL** automatisées, avec une rétention définie, stockées hors de l'hôte (TAD §9.4).
- [ ] **Restauration testée** : restauration complète sur un environnement vierge, avec un procès-verbal daté.
- [ ] **Procédure de reprise après incident** documentée dans `docs/deployment/` (TAD §9.4).
- [ ] **TLS** : émission et renouvellement Let's Encrypt validés, par le webroot ACME, avec un rechargement de Nginx après chaque renouvellement (brouillon ci-dessous).
- [ ] **Secrets de production** : clés JWT (RSA 3072) et HMAC, mots de passe des rôles PostgreSQL ; générés et stockés hors du dépôt, avec une procédure de rotation.
- [ ] **Bootstrap** : variables `SENTINEL_BOOTSTRAP_ADMIN_*` retirées de `.env` après la première connexion de l'administrateur.
- [ ] **Watchdog externe** sur l'endpoint public (TAD §9.5), ou acceptation explicite et datée de son absence.

Toutes les commandes ci-dessous s'exécutent depuis la racine du dépôt, avec la combinaison prod :

```bash
PROD="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
```

## Changer le mot de passe d'un rôle PostgreSQL

`infrastructure/postgres/init/01-roles.sh` ne s'exécute que sur un volume vide : un mot de passe modifié dans `.env` n'est **pas** appliqué à une base existante. Pour `sentinel_app` (variable `SENTINEL_DB_APP_PASSWORD`) ou `sentinel_owner` (`SENTINEL_DB_OWNER_PASSWORD`) :

1. Générer un mot de passe long et aléatoire, par exemple `openssl rand -base64 32`.
2. L'appliquer avec le superutilisateur. `\password` envoie le mot de passe déjà chiffré, sans le laisser dans l'historique du shell ni dans les journaux de PostgreSQL :

   ```bash
   $PROD exec postgres psql -U postgres -d sentinel
   ```

   ```sql
   \password sentinel_app
   ```

3. Reporter le même mot de passe dans `.env`.
4. Recréer les conteneurs qui l'utilisent, pour qu'ils relisent `.env` : `migrate` (propriétaire) puis `core-api` (applicatif), qui attend la fin de `migrate`.

   ```bash
   $PROD up -d --force-recreate migrate core-api
   ```

5. Vérifier : `$PROD ps` montre `migrate` terminé avec le code 0 et `core-api` en bonne santé.

Le mot de passe du superutilisateur (`POSTGRES_PASSWORD`) se change de la même façon (`\password postgres`). Il ne sert qu'à l'initialisation et à l'administration, et n'est transmis à aucun autre service.

## Renouvellement TLS (brouillon, à valider sur l'environnement cible)

Prérequis : un nom DNS qui pointe vers l'hôte, les ports 80 et 443 ouverts, et dans `.env` :

- `SENTINEL_TLS_CERT_DIR` : dossier lu par le gateway, qui contient `fullchain.pem` et `privkey.pem` ;
- `SENTINEL_ACME_WEBROOT` : dossier servi sur `/.well-known/acme-challenge/` par le port 80.

**Première émission.** Le gateway ne démarre pas sans certificat. Deux possibilités : placer un certificat autosigné temporaire dans `SENTINEL_TLS_CERT_DIR` le temps de la première émission par le webroot, ou émettre le premier certificat avec `certbot certonly --standalone` avant de démarrer le gateway.

**Émission et renouvellement par le webroot.** Certbot écrit dans `/etc/letsencrypt/live/<domaine>/` des liens symboliques vers `../../archive/`, qui ne fonctionneraient pas une fois montés dans le conteneur : un hook de déploiement copie donc les fichiers, puis recharge Nginx sans interruption.

```bash
certbot certonly --webroot -w "$SENTINEL_ACME_WEBROOT" -d sentinel.example.org \
  --deploy-hook '
    install -m 0644 "$RENEWED_LINEAGE/fullchain.pem" "$SENTINEL_TLS_CERT_DIR/fullchain.pem" &&
    install -m 0600 "$RENEWED_LINEAGE/privkey.pem" "$SENTINEL_TLS_CERT_DIR/privkey.pem" &&
    docker compose -f /chemin/vers/vps-sentinel/docker-compose.yml -f /chemin/vers/vps-sentinel/docker-compose.prod.yml \
      exec gateway nginx -s reload'
```

Certbot conserve ce hook pour les renouvellements, déclenchés par son timer (`certbot renew`, deux fois par jour). À vérifier :

- `certbot renew --dry-run` réussit ;
- après un renouvellement, `openssl s_client -connect sentinel.example.org:443 -servername sentinel.example.org </dev/null | openssl x509 -noout -dates` montre la nouvelle date d'expiration ;
- l'en-tête `Strict-Transport-Security` est présent sur les réponses HTTPS.
