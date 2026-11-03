#!/usr/bin/env bash
# Génère les secrets de développement (spec §6.6) dans infrastructure/secrets/dev/, dossier ignoré par git :
# paire RSA 3072 bits pour signer les JWT, clé HMAC de 32 octets (base64).
# N'écrase rien sans l'option --force.
set -euo pipefail

force=false
case "${1:-}" in
  --force) force=true ;;
  "") ;;
  *)
    echo "Usage : $0 [--force]" >&2
    exit 2
    ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dir="$root/infrastructure/secrets/dev"
files=(jwt-private.pem jwt-public.pem hmac.key)

if [[ "$force" == false ]]; then
  for f in "${files[@]}"; do
    if [[ -e "$dir/$f" ]]; then
      echo "Refus : $dir/$f existe déjà. Relancer avec --force pour régénérer tous les secrets." >&2
      exit 1
    fi
  done
fi

umask 077
mkdir -p "$dir"
cd "$dir"
# L'openssl de Git Bash est un binaire Windows qui écrit en CRLF : on force LF pour des fichiers identiques partout.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 | tr -d '\r' >jwt-private.pem
openssl pkey -in jwt-private.pem -pubout | tr -d '\r' >jwt-public.pem
openssl rand -base64 32 | tr -d '\r' >hmac.key
# Compose monte ces fichiers avec leurs droits d'origine : le conteneur core-api (uid 10001) doit pouvoir les lire.
# Clés jetables de développement ; en production, les secrets restent réservés à l'uid 10001 (docs/deployment).
chmod 0644 jwt-private.pem jwt-public.pem hmac.key
echo "Secrets de développement générés dans $dir"
