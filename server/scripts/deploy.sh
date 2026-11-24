#!/usr/bin/env bash
# Deploys one WildFly image on the VM. Called by .github/workflows/deploy.yml, after the code in
# /opt/smartparking has been updated to the deployed commit. Can also be run by hand on the VM:
#
#   bash /opt/smartparking/server/scripts/deploy.sh ghcr.io/<owner>/<repo>/wildfly:<commit>
#
# Rollback: run it again with the tag of an older commit (tags are listed on the GHCR package page).
#
# Steps: write the image in .env (so a later manual "docker compose up -d" keeps the same image),
# pull it, restart the containers whose image or configuration changed, reload Nginx
# (nginx.conf and the frontend are mounted from the Git checkout), then wait until /api/health answers.
set -euo pipefail

IMAGE="${1:?usage: deploy.sh <image:tag>}"
HEALTH_TIMEOUT_S="${HEALTH_TIMEOUT_S:-180}"

cd "$(dirname "$0")/.."      # server/

if [ ! -f .env ]; then
  echo "server/.env is missing: create it first (docs/installation-serveur.md)." >&2
  exit 1
fi

# ---------- One-time migration of the English refactoring (docs/installation-serveur.md §4.6) ----------
# Old French keys of .env renamed to the English ones; values (passwords) kept. Idempotent.
for pair in DOMAINE:DOMAIN MONGO_UTILISATEUR:MONGO_USER MONGO_MOT_DE_PASSE:MONGO_PASSWORD \
            MONGO_PORT_HOTE:MONGO_HOST_PORT MQTT_MDP_PI:MQTT_PASSWORD_PI MQTT_MDP_SERVEUR:MQTT_PASSWORD_SERVER \
            EXPLOITANT_EMAIL:OPERATOR_EMAIL EXPLOITANT_MOT_DE_PASSE:OPERATOR_PASSWORD; do
  old=${pair%%:*}; new=${pair##*:}
  if grep -q "^${old}=" .env && ! grep -q "^${new}=" .env; then
    sed -i "s/^${old}=/${new}=/" .env
    echo "==> .env: ${old} renamed to ${new}"
  fi
done
# Single JWT key (jwt/privee.pem) -> key folder with rotation (jwt/keys/<kid>.pem).
if [ -f jwt/privee.pem ] && [ -z "$(ls -A jwt/keys 2>/dev/null)" ]; then
  mkdir -p jwt/keys
  cp -p jwt/privee.pem "jwt/keys/$(date -u +%Y-%m).pem"
  echo "==> JWT key copied to jwt/keys/$(date -u +%Y-%m).pem"
fi
if [ -z "$(ls -A jwt/keys 2>/dev/null)" ]; then
  echo "No JWT key in server/jwt/keys/: run scripts/generate-jwt-keys.sh first." >&2
  exit 1
fi

# WILDFLY_IMAGE=<image> in .env, replaced if present, added otherwise.
if grep -q '^WILDFLY_IMAGE=' .env; then
  sed -i "s|^WILDFLY_IMAGE=.*|WILDFLY_IMAGE=${IMAGE}|" .env
else
  printf '\n# Set by scripts/deploy.sh: image deployed on this machine\nWILDFLY_IMAGE=%s\n' "$IMAGE" >> .env
fi

echo "==> Pulling ${IMAGE}"
docker compose pull --quiet wildfly

echo "==> Starting the containers"
docker compose up -d --no-build

echo "==> Reloading Nginx (configuration and frontend come from the Git checkout)"
docker compose exec -T nginx nginx -t -q
docker compose exec -T nginx nginx -s reload

echo "==> Waiting for /api/health (at most ${HEALTH_TIMEOUT_S} s)"
deadline=$(( $(date +%s) + HEALTH_TIMEOUT_S ))
until docker compose exec -T nginx wget -q -O - http://wildfly:8080/api/health; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo >&2
    echo "WildFly did not answer in time. Last log lines:" >&2
    docker compose logs --tail 50 wildfly >&2
    exit 1
  fi
  sleep 5
done
echo

# Free disk space (20 GB disk): remove images that are no longer used by any container.
docker image prune --force > /dev/null

echo "==> Deployed ${IMAGE}"
