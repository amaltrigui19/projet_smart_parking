#!/usr/bin/env bash
# Génère la paire de clés RSA des JWT (RS256, contrat §2.2) dans server/jwt/ (ignoré par Git) :
#   server/jwt/privee.pem   clé PRIVÉE : signe les jetons (module iam, à la connexion). Secrète.
#   server/jwt/publique.pem clé publique : vérifie les jetons (MicroProfile JWT de WildFly).
# docker-compose.yml monte ce dossier en lecture seule dans le conteneur wildfly (/opt/smartparking/jwt).
#
# Usage (depuis la racine du dépôt) :
#   ./server/scripts/generer-cles-jwt.sh            # refuse d'écraser des clés existantes
#   ./server/scripts/generer-cles-jwt.sh --forcer   # nouvelles clés : tous les jetons émis deviennent invalides
#
# Fonctionne dans Git Bash (Windows) et sous Linux. Nécessite openssl.
set -euo pipefail

cd "$(dirname "$0")/.."      # dossier server/
mkdir -p jwt

if [ -f jwt/privee.pem ] && [ "${1:-}" != "--forcer" ]; then
  echo "Les clés existent déjà dans server/jwt/ : rien à faire."
  echo "Pour les remplacer (les jetons déjà émis seront refusés) : $0 --forcer"
  exit 0
fi

umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt/privee.pem 2>/dev/null
openssl pkey -in jwt/privee.pem -pubout -out jwt/publique.pem
chmod 600 jwt/privee.pem
chmod 644 jwt/publique.pem

echo "Clés JWT créées :"
echo "  server/jwt/privee.pem   (privée, PKCS#8)  -> lue par WildFly pour SIGNER les jetons (JWT_CLE_PRIVEE_FICHIER)"
echo "  server/jwt/publique.pem (publique)        -> lue par MicroProfile JWT pour VÉRIFIER les jetons"
echo "Ne jamais commiter ni envoyer privee.pem. Sur la VM, le fichier doit appartenir à l'uid 1000"
echo "(utilisateur jboss du conteneur) : sudo chown 1000:1000 server/jwt/privee.pem si vous l'avez créé avec sudo."
