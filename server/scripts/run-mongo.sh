#!/usr/bin/env bash
# Exécute un script mongosh dans le conteneur mongo, sur la base smartparking.
# Usage (depuis la racine du dépôt) : ./server/scripts/run-mongo.sh server/mongo/seed.js
set -euo pipefail
file="${1:?usage : $0 <script.js>}"
compose="docker compose -f server/docker-compose.yml"
$compose cp "$file" mongo:/tmp/script.js
$compose exec -T mongo sh -c \
  'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin smartparking --file /tmp/script.js'
