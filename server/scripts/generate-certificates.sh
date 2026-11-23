#!/usr/bin/env bash
# Génère une autorité de certification (CA) locale et un certificat serveur
# pour Nginx (HTTPS) et Mosquitto (MQTT TLS), dans server/certs/.
#
# Usage (depuis la racine du dépôt ou depuis server/) :
#   ./server/scripts/generate-certificates.sh                    # localhost + DOMAIN du .env
#   ./server/scripts/generate-certificates.sh 192.168.1.20       # + adresse IP du PC (pour le Pi)
#
# Fonctionne dans Git Bash (Windows) et sous Linux. Nécessite openssl.
# Sur la VM, ce certificat est temporaire : il est remplacé par Let's Encrypt.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash : ne pas transformer "/CN=..." en chemin Windows

cd "$(dirname "$0")/.."      # dossier server/
mkdir -p certs
C=certs

DOMAIN=localhost
if [ -f .env ]; then
  DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2- | tr -d '\r' || true)
  DOMAIN=${DOMAIN:-localhost}
fi

# Noms et adresses acceptés par le certificat
SAN="DNS:localhost,IP:127.0.0.1,DNS:mqtt"
[ "$DOMAIN" != "localhost" ] && SAN="$SAN,DNS:$DOMAIN"
for name in "$@"; do
  if [[ "$name" =~ ^[0-9.]+$ ]]; then SAN="$SAN,IP:$name"; else SAN="$SAN,DNS:$name"; fi
done

# 1. Autorité locale : créée une seule fois (on ne la refait pas, pour ne pas
#    devoir la réimporter dans Windows à chaque fois)
if [ ! -f "$C/ca.pem" ]; then
  openssl genrsa -out "$C/ca-key.pem" 4096 2>/dev/null
  openssl req -x509 -new -key "$C/ca-key.pem" -sha256 -days 825 \
    -subj "/CN=Smart Parking CA locale" -out "$C/ca.pem"
  echo "CA locale créée : server/$C/ca.pem"
fi

# 2. Certificat du serveur, signé par la CA locale
openssl genrsa -out "$C/privkey.pem" 2048 2>/dev/null
openssl req -new -key "$C/privkey.pem" -subj "/CN=$DOMAIN" -out "$C/server.csr"
cat > "$C/extensions.cnf" <<EOF
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = $SAN
EOF
openssl x509 -req -in "$C/server.csr" -CA "$C/ca.pem" -CAkey "$C/ca-key.pem" \
  -CAcreateserial -days 825 -sha256 -extfile "$C/extensions.cnf" \
  -out "$C/server.pem" 2>/dev/null
cat "$C/server.pem" "$C/ca.pem" > "$C/fullchain.pem"
rm -f "$C/server.csr" "$C/extensions.cnf"

# 3. Droits : Mosquitto tourne avec l'utilisateur 1883 dans son conteneur
chmod 644 "$C/fullchain.pem" "$C/ca.pem"
if chgrp 1883 "$C/privkey.pem" 2>/dev/null; then
  chmod 640 "$C/privkey.pem"
else
  chmod 644 "$C/privkey.pem"
fi

echo "Certificat serveur créé : server/$C/fullchain.pem et server/$C/privkey.pem"
echo "Valable pour : $SAN"
