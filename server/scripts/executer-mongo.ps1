# Exécute un script mongosh dans le conteneur mongo, sur la base smartparking.
# Usage (depuis la racine du dépôt) : .\server\scripts\executer-mongo.ps1 server\mongo\seed.js
param([Parameter(Mandatory = $true)][string]$Fichier)
$ErrorActionPreference = "Stop"
docker compose -f server\docker-compose.yml cp $Fichier mongo:/tmp/script.js
docker compose -f server\docker-compose.yml exec -T mongo sh -c 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin smartparking --file /tmp/script.js'