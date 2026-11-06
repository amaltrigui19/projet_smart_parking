#!/bin/sh
# Crée le fichier des mots de passe Mosquitto à partir des variables d'environnement
# (fichier .env), puis lance le broker. Aucun mot de passe n'est écrit dans Git.
set -eu

: "${MQTT_MDP_PI:?MQTT_MDP_PI manquant dans .env}"
: "${MQTT_MDP_SERVEUR:?MQTT_MDP_SERVEUR manquant dans .env}"

D=/mosquitto/data/securite
mkdir -p "$D"
rm -f "$D/passwd"
mosquitto_passwd -c -b "$D/passwd" pi "$MQTT_MDP_PI"
mosquitto_passwd -b "$D/passwd" serveur "$MQTT_MDP_SERVEUR"
cp /mosquitto/config/acl "$D/acl"

chown -R mosquitto:mosquitto "$D"
chmod 700 "$D"
chmod 600 "$D/passwd" "$D/acl"

exec mosquitto -c /mosquitto/config/mosquitto.conf
