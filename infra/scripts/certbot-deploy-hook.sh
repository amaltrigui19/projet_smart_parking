#!/bin/sh
# VM cloud uniquement. Lancé par Certbot après chaque obtention ou renouvellement
# d'un certificat Let's Encrypt : copie le certificat dans infra/certs/ puis
# recharge Nginx et redémarre Mosquitto.
#
# Installation (une fois) :
#   sudo cp /opt/smartparking/infra/scripts/certbot-deploy-hook.sh \
#           /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
#   sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/smartparking.sh
#
# Certbot fournit RENEWED_LINEAGE, sur notre VM :
#   /etc/letsencrypt/live/smartparking-supcom.duckdns.org
set -eu

INFRA=${INFRA:-/opt/smartparking/infra}
: "${RENEWED_LINEAGE:?lancer avec RENEWED_LINEAGE=/etc/letsencrypt/live/<domaine>}"

mkdir -p "$INFRA/certs"
cp -L "$RENEWED_LINEAGE/fullchain.pem" "$INFRA/certs/fullchain.pem"
cp -L "$RENEWED_LINEAGE/privkey.pem"   "$INFRA/certs/privkey.pem"
chmod 644 "$INFRA/certs/fullchain.pem"
chown root:1883 "$INFRA/certs/privkey.pem"   # 1883 = utilisateur mosquitto du conteneur
chmod 640 "$INFRA/certs/privkey.pem"

cd "$INFRA"
# Recharge seulement si la stack Docker tourne (sinon le certificat sera lu au démarrage)
if docker compose ps --status running --services 2>/dev/null | grep -qx nginx; then
  docker compose exec -T nginx nginx -s reload
fi
if docker compose ps --status running --services 2>/dev/null | grep -qx mqtt; then
  docker compose restart mqtt
fi
echo "Certificat Let's Encrypt copié dans $INFRA/certs pour Nginx et Mosquitto."
