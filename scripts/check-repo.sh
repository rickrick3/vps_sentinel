#!/usr/bin/env bash
# Contrôles d'hygiène du dépôt (spec §5.3 et §13.9), réutilisés par la CI.
# Les modes et les fins de ligne sont lus dans l'index git : un nouveau fichier doit être indexé (git add) pour être vérifié.
set -euo pipefail

cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"

errors=0
fail() {
  echo "ÉCHEC : $*" >&2
  errors=$((errors + 1))
}

# Fichiers d'hygiène présents
for f in .gitattributes .gitignore .editorconfig; do
  [[ -f "$f" ]] || fail "$f absent"
done

# Fins de ligne LF partout, sauf *.cmd et *.bat (index et copie de travail des fichiers non ignorés)
while IFS=$'\t' read -r info path; do
  case "$path" in
    *.cmd | *.bat) continue ;;
  esac
  case "$info" in
    *i/crlf* | *w/crlf* | *i/mixed* | *w/mixed*) fail "fin de ligne CRLF : $path ($info)" ;;
  esac
done < <(git ls-files --eol --cached --others --exclude-standard)

# Bit exécutable sur mvnw et les scripts shell
while read -r mode _ _ path; do
  [[ "$mode" == "100755" ]] || fail "bit exécutable absent dans l'index : $path (mode $mode)"
done < <(git ls-files -s -- services/core-api/mvnw 'scripts/*.sh' 'infrastructure/postgres/init/*.sh')

# Aucun fichier sensible suivi par git
while IFS= read -r path; do
  fail "fichier sensible suivi par git : $path"
done < <(git ls-files -- '.env' '*/.env' '.env.*' '*/.env.*' '*.pem' '*.key' 'infrastructure/secrets/' \
  | grep -Ev '(^|/)\.env\.example$' || true)

if ((errors > 0)); then
  echo "$errors écart(s) détecté(s)." >&2
  exit 1
fi
echo "OK"
