#!/bin/sh
# VM cloud uniquement. Lancé par Certbot après chaque obtention ou renouvellement
# d'un certificat Let's Encrypt : copie le certificat dans server/certs/ puis
# recharge Nginx et redémarre Mosquitto.
#
# Installation (une fois) :
#   sudo cp /opt/smartparking/server/scripts/certbot-deploy-hook.sh \
#           /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
#   sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
#
# Certbot fournit RENEWED_LINEAGE, sur notre VM :
#   /etc/letsencrypt/live/smartparking-supcom.duckdns.org
set -eu

SERVER_DIR=${SERVER_DIR:-/opt/smartparking/server}
: "${RENEWED_LINEAGE:?lancer avec RENEWED_LINEAGE=/etc/letsencrypt/live/<domaine>}"

mkdir -p "$SERVER_DIR/certs"
cp -L "$RENEWED_LINEAGE/fullchain.pem" "$SERVER_DIR/certs/fullchain.pem"
cp -L "$RENEWED_LINEAGE/privkey.pem"   "$SERVER_DIR/certs/privkey.pem"
chmod 644 "$SERVER_DIR/certs/fullchain.pem"
chown root:1883 "$SERVER_DIR/certs/privkey.pem"   # 1883 = utilisateur mosquitto du conteneur
chmod 640 "$SERVER_DIR/certs/privkey.pem"

cd "$SERVER_DIR"
# Recharge seulement si la stack Docker tourne (sinon le certificat sera lu au démarrage)
if docker compose ps --status running --services 2>/dev/null | grep -qx nginx; then
  docker compose exec -T nginx nginx -s reload
fi
if docker compose ps --status running --services 2>/dev/null | grep -qx mqtt; then
  docker compose restart mqtt
fi
echo "Certificat Let's Encrypt copié dans $SERVER_DIR/certs pour Nginx et Mosquitto."
