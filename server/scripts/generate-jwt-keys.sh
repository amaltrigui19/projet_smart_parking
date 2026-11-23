#!/usr/bin/env bash
# Crée une clé de signature des JWT (RSA 2048, RS256, contrat §2.2) dans server/jwt/keys/ (ignoré par Git).
# Le nom du fichier est le « kid » de la clé : par défaut l'année et le mois, par exemple 2026-11.pem.
#
# Le module IAM (WildFly) lit ce dossier toutes les 60 s : il signe avec la DERNIÈRE clé privée par ordre
# alphabétique et publie toutes les clés publiques sur /.well-known/jwks.json. L'API vérifie les jetons
# avec ce JWKS : elle n'a besoin d'aucun fichier de clé. Détails et rotation : middleware/iam/README.md.
#
# Usage (depuis la racine du dépôt) :
#   ./server/scripts/generate-jwt-keys.sh              # crée jwt/keys/<AAAA-MM>.pem s'il n'existe pas
#   ./server/scripts/generate-jwt-keys.sh 2026-12      # crée jwt/keys/2026-12.pem : rotation, elle signera
#                                                      # les nouveaux jetons, les anciens restent valables
#   ./server/scripts/generate-jwt-keys.sh --retire 2026-11
#                                                      # garde seulement la clé publique de 2026-11 : elle ne
#                                                      # signe plus, ses jetons restent vérifiables (1 h)
#
# Fonctionne dans Git Bash (Windows) et sous Linux. Nécessite openssl.
set -euo pipefail

cd "$(dirname "$0")/.."      # dossier server/
KEYS_DIR=jwt/keys
mkdir -p "$KEYS_DIR"
umask 077

check_kid() {
  if [[ ! "$1" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "kid invalide : « $1 » (lettres, chiffres, . _ - seulement)" >&2
    exit 1
  fi
}

# --retire <kid> : remplace la clé privée par sa clé publique seule
if [ "${1:-}" = "--retire" ]; then
  kid="${2:?usage : $0 --retire <kid>}"
  check_kid "$kid"
  file="$KEYS_DIR/$kid.pem"
  [ -f "$file" ] || { echo "Aucune clé $file" >&2; exit 1; }
  openssl pkey -in "$file" -pubout -out "$file.pub"
  mv "$file.pub" "$file"
  chmod 644 "$file"
  echo "Clé $kid retirée : seule sa clé publique reste publiée. La supprimer dans 1 h (durée de vie des jetons)."
  exit 0
fi

kid="${1:-$(date -u +%Y-%m)}"
check_kid "$kid"
file="$KEYS_DIR/$kid.pem"

if [ -f "$file" ]; then
  echo "La clé $file existe déjà : rien à faire."
  echo "Pour une rotation, donner un nouveau kid : $0 $(date -u +%Y-%m-%d)"
  exit 0
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$file" 2>/dev/null
# Lisible par l'utilisateur de WildFly dans le conteneur (même uid que le propriétaire du fichier sur la VM)
chmod 600 "$file"

echo "Clé JWT créée : server/$file (kid = $kid, privée, PKCS#8)"
echo "Clés présentes (la dernière par ordre alphabétique signe) :"
ls -1 "$KEYS_DIR"
