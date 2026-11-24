#!/bin/sh
# Crée le fichier des mots de passe Mosquitto à partir des variables d'environnement
# (fichier .env), puis lance le broker. Aucun mot de passe n'est écrit dans Git.
set -eu

: "${MQTT_PASSWORD_PI:?MQTT_PASSWORD_PI manquant dans .env}"
: "${MQTT_PASSWORD_SERVER:?MQTT_PASSWORD_SERVER manquant dans .env}"

DIR=/mosquitto/data/security
mkdir -p "$DIR"
rm -f "$DIR/passwd"
mosquitto_passwd -c -b "$DIR/passwd" pi "$MQTT_PASSWORD_PI"
mosquitto_passwd -b "$DIR/passwd" server "$MQTT_PASSWORD_SERVER"
cp /mosquitto/config/acl "$DIR/acl"

chown -R mosquitto:mosquitto "$DIR"
chmod 700 "$DIR"
chmod 600 "$DIR/passwd" "$DIR/acl"

exec mosquitto -c /mosquitto/config/mosquitto.conf
