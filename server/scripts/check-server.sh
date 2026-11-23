#!/usr/bin/env bash
# Vérifie que le serveur fonctionne : HTTPS, API, JWKS, MQTT en TLS, MongoDB.
# Usage : ./server/scripts/check-server.sh            (local)
#         ./server/scripts/check-server.sh <domaine>  (VM, depuis la VM ou depuis le PC)
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."      # dossier server/

HOST=${1:-localhost}
set -a; . ./.env; set +a
MQTT_PASSWORD_PI=$(echo "$MQTT_PASSWORD_PI" | tr -d '\r')
MQTT_PASSWORD_SERVER=$(echo "$MQTT_PASSWORD_SERVER" | tr -d '\r')
MONGO_PASSWORD=$(echo "$MONGO_PASSWORD" | tr -d '\r')
MONGO_USER=$(echo "$MONGO_USER" | tr -d '\r')

# curl sans MSYS_NO_PATHCONV (sinon le curl de Windows ne comprend pas /dev/null)
request() { env -u MSYS_NO_PATHCONV curl "$@"; }

# Certificat local (CA maison) ou Let's Encrypt (CA du système)
CURL_CA=(); if [ "$HOST" = "localhost" ] && [ -f certs/ca.pem ]; then CURL_CA=(--cacert certs/ca.pem); fi
# curl de Windows (Schannel) : ne pas chercher la liste de révocation d'une CA locale
request --version | grep -q Schannel && CURL_CA+=(--ssl-no-revoke)

failed=0
ok()   { echo "  [OK]    $1"; }
ko()   { echo "  [ECHEC] $1"; failed=1; }

echo "1. HTTPS : front office https://$HOST/ et back office https://$HOST/admin/"
code=$(request -s -o /dev/null -w '%{http_code}' "${CURL_CA[@]}" "https://$HOST/")
[ "$code" = "200" ] && ok "front office : $code" || ko "front office : code $code"
code=$(request -s -o /dev/null -w '%{http_code}' "${CURL_CA[@]}" "https://$HOST/admin/")
[ "$code" = "200" ] && ok "back office : $code" || ko "back office : code $code"

echo "2. Redirection HTTP -> HTTPS"
loc=$(request -s -o /dev/null -w '%{redirect_url}' "http://$HOST/")
[[ "$loc" == https://* ]] && ok "http:// redirige vers $loc" || ko "pas de redirection ($loc)"

echo "3. API WildFly : GET /api/health"
body=$(request -s "${CURL_CA[@]}" "https://$HOST/api/health")
[[ "$body" == *'"status":"ok"'* ]] && ok "$body" || ko "réponse : $body"

echo "4. IAM : GET /.well-known/jwks.json"
body=$(request -s "${CURL_CA[@]}" "https://$HOST/.well-known/jwks.json")
if [[ "$body" == *'"kid"'* && "$body" == *'"kty":"RSA"'* && "$body" != *'"d":'* ]]; then
  ok "clés publiques publiées (sans partie privée)"
else
  ko "JWKS : $body"
fi

# Clients MQTT : dans le conteneur en local, sur la machine hôte sur la VM
if [ "$HOST" = "localhost" ]; then
  MQ=(docker compose exec -T mqtt); CA_MQTT=(--cafile /mosquitto/certs/ca.pem)
else
  MQ=(); CA_MQTT=(--capath /etc/ssl/certs)
  command -v mosquitto_pub >/dev/null || echo "  (installer les clients : sudo apt install -y mosquitto-clients)"
fi

echo "5. MQTT en TLS (port 8883) : publication par 'pi', réception par 'server'"
MESSAGE='{"online":true,"fps":0,"cpu_temp":0}'
received_file=$(mktemp)
"${MQ[@]}" mosquitto_sub -h "$HOST" -p 8883 "${CA_MQTT[@]}" -u server -P "$MQTT_PASSWORD_SERVER" \
  -t parking/P1/pi/status -C 1 -W 10 > "$received_file" 2>&1 &
sleep 2
"${MQ[@]}" mosquitto_pub -h "$HOST" -p 8883 "${CA_MQTT[@]}" -u pi -P "$MQTT_PASSWORD_PI" \
  -t parking/P1/pi/status -m "$MESSAGE"
wait
received=$(tr -d '\r' < "$received_file"); rm -f "$received_file"
[ "$received" = "$MESSAGE" ] && ok "message reçu : $received" || ko "message non reçu ($received)"

echo "6. MQTT : connexion anonyme refusée"
if "${MQ[@]}" mosquitto_pub -h "$HOST" -p 8883 "${CA_MQTT[@]}" -t parking/P1/pi/status -m x 2>/dev/null; then
  ko "une connexion sans mot de passe a été acceptée"
else
  ok "connexion sans mot de passe refusée"
fi

echo "7. MongoDB"
r=$(docker compose exec -T mongo mongosh --quiet -u "$MONGO_USER" -p "$MONGO_PASSWORD" \
  --authenticationDatabase admin --eval "db.adminCommand('ping').ok" 2>/dev/null | tr -d '\r')
[ "$r" = "1" ] && ok "ping MongoDB avec authentification" || ko "MongoDB ne répond pas ($r)"

echo
[ $failed -eq 0 ] && echo "Tout fonctionne." || echo "Au moins une vérification a échoué : voir docs/installation-serveur.md, section Dépannage."
exit $failed
