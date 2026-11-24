# Exécute un script mongosh dans le conteneur mongo, sur la base smartparking.
# Usage (depuis la racine du dépôt) : .\server\scripts\run-mongo.ps1 server\mongo\seed.js
param([Parameter(Mandatory = $true)][string]$File)
$ErrorActionPreference = "Stop"
docker compose -f server\docker-compose.yml cp $File mongo:/tmp/script.js
docker compose -f server\docker-compose.yml exec -T mongo sh -c 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin smartparking --file /tmp/script.js'
