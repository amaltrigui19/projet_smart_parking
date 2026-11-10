#!/usr/bin/env bash
# Vérifie que le serveur fonctionne : HTTPS, API, MQTT en TLS, MongoDB.
# Usage : ./server/scripts/verifier.sh            (local)
#         ./server/scripts/verifier.sh <domaine>  (VM, depuis la VM ou depuis le PC)
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/.."      # dossier server/

HOTE=${1:-localhost}
set -a; . ./.env; set +a
MQTT_MDP_PI=$(echo "$MQTT_MDP_PI" | tr -d '\r')
MQTT_MDP_SERVEUR=$(echo "$MQTT_MDP_SERVEUR" | tr -d '\r')
MONGO_MOT_DE_PASSE=$(echo "$MONGO_MOT_DE_PASSE" | tr -d '\r')
MONGO_UTILISATEUR=$(echo "$MONGO_UTILISATEUR" | tr -d '\r')

# curl sans MSYS_NO_PATHCONV (sinon le curl de Windows ne comprend pas /dev/null)
requete() { env -u MSYS_NO_PATHCONV curl "$@"; }

# Certificat local (CA maison) ou Let's Encrypt (CA du système)
CURL_CA=(); if [ "$HOTE" = "localhost" ] && [ -f certs/ca.pem ]; then CURL_CA=(--cacert certs/ca.pem); fi
# curl de Windows (Schannel) : ne pas chercher la liste de révocation d'une CA locale
requete --version | grep -q Schannel && CURL_CA+=(--ssl-no-revoke)

echec=0
ok()   { echo "  [OK]    $1"; }
ko()   { echo "  [ECHEC] $1"; echec=1; }

echo "1. HTTPS sur https://$HOTE/"
code=$(requete -s -o /dev/null -w '%{http_code}' "${CURL_CA[@]}" "https://$HOTE/")
[ "$code" = "200" ] && ok "page d'accueil (PWA) : $code" || ko "page d'accueil : code $code"

echo "2. Redirection HTTP -> HTTPS"
loc=$(requete -s -o /dev/null -w '%{redirect_url}' "http://$HOTE/")
[[ "$loc" == https://* ]] && ok "http:// redirige vers $loc" || ko "pas de redirection ($loc)"

echo "3. API WildFly : GET /api/sante"
corps=$(requete -s "${CURL_CA[@]}" "https://$HOTE/api/sante")
[[ "$corps" == *'"statut":"ok"'* ]] && ok "$corps" || ko "réponse : $corps"

# Clients MQTT : dans le conteneur en local, sur la machine hôte sur la VM
if [ "$HOTE" = "localhost" ]; then
  MQ=(docker compose exec -T mqtt); CA_MQTT=(--cafile /mosquitto/certs/ca.pem)
else
  MQ=(); CA_MQTT=(--capath /etc/ssl/certs)
  command -v mosquitto_pub >/dev/null || echo "  (installer les clients : sudo apt install -y mosquitto-clients)"
fi

echo "4. MQTT en TLS (port 8883) : publication par 'pi', réception par 'serveur'"
recu_fichier=$(mktemp)
"${MQ[@]}" mosquitto_sub -h "$HOTE" -p 8883 "${CA_MQTT[@]}" -u serveur -P "$MQTT_MDP_SERVEUR" \
  -t parking/P1/pi/statut -C 1 -W 10 > "$recu_fichier" 2>&1 &
sleep 2
"${MQ[@]}" mosquitto_pub -h "$HOTE" -p 8883 "${CA_MQTT[@]}" -u pi -P "$MQTT_MDP_PI" \
  -t parking/P1/pi/statut -m '{"en_ligne":true,"fps":0,"temp_cpu":0}'
wait
recu=$(tr -d '\r' < "$recu_fichier"); rm -f "$recu_fichier"
[ "$recu" = '{"en_ligne":true,"fps":0,"temp_cpu":0}' ] && ok "message reçu : $recu" || ko "message non reçu ($recu)"

echo "5. MQTT : connexion anonyme refusée"
if "${MQ[@]}" mosquitto_pub -h "$HOTE" -p 8883 "${CA_MQTT[@]}" -t parking/P1/pi/statut -m x 2>/dev/null; then
  ko "une connexion sans mot de passe a été acceptée"
else
  ok "connexion sans mot de passe refusée"
fi

echo "6. MongoDB"
r=$(docker compose exec -T mongo mongosh --quiet -u "$MONGO_UTILISATEUR" -p "$MONGO_MOT_DE_PASSE" \
  --authenticationDatabase admin --eval "db.adminCommand('ping').ok" 2>/dev/null | tr -d '\r')
[ "$r" = "1" ] && ok "ping MongoDB avec authentification" || ko "MongoDB ne répond pas ($r)"

echo
[ $echec -eq 0 ] && echo "Tout fonctionne." || echo "Au moins une vérification a échoué : voir docs/installation-serveur.md, section Dépannage."
exit $echec
