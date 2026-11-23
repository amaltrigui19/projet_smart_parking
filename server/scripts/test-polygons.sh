#!/usr/bin/env bash
# Teste GET et PUT /api/config/polygons (contrat §2.10) sur un serveur lancé :
# rôles (401 sans jeton, 403 conducteur), PUT valide puis relecture, 400 invalid_config, JSON mal formé.
# En local, vérifie aussi que le PUT ne touche pas à l'état des places (venant du Pi).
# Le fichier de polygones présent au départ est remis à la fin.
#
# Usage (depuis la racine du dépôt, serveur lancé, données d'exemple chargées : server/mongo/seed.js) :
#   OPERATOR_EMAIL=... OPERATOR_PASSWORD=... ./server/scripts/test-polygons.sh
#   OPERATOR_EMAIL=... OPERATOR_PASSWORD=... ./server/scripts/test-polygons.sh https://smartparking-supcom.duckdns.org
# Nécessite curl et python. Code de sortie : 0 si tout passe.
set -uo pipefail
cd "$(dirname "$0")/.."      # dossier server/

: "${OPERATOR_EMAIL:?définir OPERATOR_EMAIL (compte opérateur, voir server/.env)}"
: "${OPERATOR_PASSWORD:?définir OPERATOR_PASSWORD}"
BASE=${1:-https://localhost}
API="$BASE/api"
CURL=(curl -s -m 20)
LOCAL=0
if [ "$BASE" = "https://localhost" ]; then
  LOCAL=1
  [ -f certs/ca.pem ] && CURL+=(--cacert certs/ca.pem)
fi
curl --version | grep -q Schannel && CURL+=(--ssl-no-revoke)   # curl de Windows et CA locale
RESPONSE=$(mktemp); BODY_FILE=$(mktemp); ORIGINAL=$(mktemp)
passed=0; failed=0

call() {  # METHODE CHEMIN TOKEN [fichier du corps] -> code HTTP ; réponse dans $RESPONSE
  local m=$1 p=$2 j=$3 f=${4:-}
  local args=(-o "$RESPONSE" -w "%{http_code}" -X "$m" "$API$p" -H "Content-Type: application/json")
  [ -n "$j" ] && args+=(-H "Authorization: Bearer $j")
  [ -n "$f" ] && args+=(--data-binary "@$f")
  env -u MSYS_NO_PATHCONV "${CURL[@]}" "${args[@]}"
}
py() { python -c "$1" "${@:2}"; }
field() { py "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(eval(sys.argv[2]))" "$RESPONSE" "$1" 2>/dev/null; }
pass() { passed=$((passed+1)); echo "  [OK]    $1"; }
fail() { failed=$((failed+1)); echo "  [ECHEC] $1 $(head -c 300 "$RESPONSE")"; }
expect() {  # nom code attendu [erreur attendue]
  local e; e=$(field "d.get('error','') if isinstance(d,dict) else ''")
  if [ "$2" = "$3" ] && { [ -z "${4:-}" ] || [ "$e" = "$4" ]; }; then pass "$1 : $2${4:+ $4}"; else fail "$1 : attendu $3${4:+ $4}, obtenu $2"; fi
}
mongo() {  # requête mongosh sur la base smartparking (local seulement)
  docker compose exec -T mongo sh -c "mongosh --quiet -u \"\$MONGO_INITDB_ROOT_USERNAME\" -p \"\$MONGO_INITDB_ROOT_PASSWORD\" --authenticationDatabase admin smartparking --eval '$1'" 2>/dev/null | tr -d '\r'
}
login() {  # email mot_de_passe -> jeton
  py "import json,sys;print(json.dumps({'email':sys.argv[1],'password':sys.argv[2]}))" "$1" "$2" > "$BODY_FILE"
  [ "$(call POST /auth/login '' "$BODY_FILE")" = "200" ] && field "d['access_token']"
}

echo "Serveur : $API"
TOKEN=$(login "$OPERATOR_EMAIL" "$OPERATOR_PASSWORD")
[ -n "$TOKEN" ] || { echo "Connexion opérateur impossible : vérifier OPERATOR_EMAIL et OPERATOR_PASSWORD."; exit 1; }
DRIVER_EMAIL="test.polygones.$(date +%s).$RANDOM@exemple.tn"
py "import json,sys;print(json.dumps({'name':'Test','email':sys.argv[1],'password':'Motdepasse8'}))" "$DRIVER_EMAIL" > "$BODY_FILE"
call POST /auth/register '' "$BODY_FILE" >/dev/null
DRIVER_TOKEN=$(login "$DRIVER_EMAIL" Motdepasse8)

echo "Rôles"
expect "GET sans jeton" "$(call GET /config/polygons '')" 401 unauthenticated
expect "GET par un conducteur" "$(call GET /config/polygons "$DRIVER_TOKEN")" 403 forbidden
expect "PUT par un conducteur" "$(call PUT /config/polygons "$DRIVER_TOKEN" "$BODY_FILE")" 403 forbidden

echo "Lecture de départ"
c=$(call GET /config/polygons "$TOKEN")
if [ "$c" = "200" ]; then cp "$RESPONSE" "$ORIGINAL"; pass "GET exploitant : 200 ($(field "len(d['spots'])") place(s) enregistrée(s))"
elif [ "$c" = "404" ]; then : > "$ORIGINAL"; pass "GET exploitant : 404 (rien d'enregistré)"
else fail "GET exploitant : $c"; fi

A1_STATE=""
if [ "$LOCAL" = 1 ]; then
  A1_STATE=$(mongo 'print(JSON.stringify(db.spots.findOne({_id:"A1"},{state:1,since:1,_id:0})))')
  mongo 'db.spots.updateOne({_id:"A1"},{$set:{state:"occupied",since:new Date("2026-11-20T10:02:11Z")}})' >/dev/null
fi

echo "Enregistrement valide"
# 8 places du plan (rectangles), A2 à 45 min, une zone : un fichier conforme au contrat §5.2
py "
import json
def rect(x,y,l=64,h=104): return [[x,y],[x+l,y],[x+l,y+h],[x,y+h]]
plan=[('A1','accessible',120),('A2','standard',45),('A3','standard',120),('A4','standard',120),
      ('B1','standard',120),('B2','standard',120),('B3','standard',120),('B4','electric',60)]
spots=[{'id':i,'type':t,'max_duration_min':d,'polygon':rect(112+64*(k%4),0 if k<4 else 216)} for k,(i,t,d) in enumerate(plan)]
print(json.dumps({'parking':'P1','resolution':[640,480],'spots':spots,
                  'forbidden_zones':[{'id':'allee1','polygon':rect(400,104,239,112)}]}))" > "$BODY_FILE"
expect "PUT valide" "$(call PUT /config/polygons "$TOKEN" "$BODY_FILE")" 200
expect "GET après PUT" "$(call GET /config/polygons "$TOKEN")" 200
[ "$(field "[p['id'] for p in d['spots']]==['A1','A2','A3','A4','B1','B2','B3','B4'] and d['spots'][1]['max_duration_min']==45 and d['forbidden_zones'][0]['polygon'][1]==[639,104] and d['resolution']==[640,480]")" = "True" ] \
  && pass "relecture conforme (8 places, A2 = 45 min, allee1 modifiée)" || fail "relecture"
[ "$(field "sorted(d)==['forbidden_zones','parking','resolution','spots'] and sorted(d['spots'][0])==['id','max_duration_min','polygon','type']")" = "True" ] \
  && pass "format du contrat §5.2 (aucun champ en plus)" || fail "format"
if [ "$LOCAL" = 1 ]; then
  [ "$(mongo 'print(db.spots.findOne({_id:"A1"}).state)')" = "occupied" ] && pass "état de A1 (venant du Pi) conservé : occupied" || fail "état de A1 effacé"
  [ "$(mongo 'print(JSON.stringify(db.parkings.findOne({_id:"P1"}).forbidden_zones[0].rect_cm))')" != "null" ] \
    && pass "rect_cm de allee1 (plan) conservé" || fail "rect_cm de allee1 perdu"
fi

echo "Erreurs"
py "
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
d['spots'][1]['id']='A1'                      # identifiant en double
d['spots'][2]['max_duration_min']=0              # durée nulle
d['spots'][3]['id']='P9'                      # absente du plan
d['spots'][4]['polygon'][1]=[640,0]              # hors de la résolution
d['spots'][5]['type']='reserved'              # type inconnu
d['spots'][6]['polygon']=d['spots'][6]['polygon'][:2]  # moins de 3 points
print(json.dumps(d))" "$BODY_FILE" > "$BODY_FILE.inv"
expect "PUT invalide" "$(call PUT /config/polygons "$TOKEN" "$BODY_FILE.inv")" 400 invalid_config
[ "$(field "all(t in d['message'] for t in ['en double','max_duration_min','P9','sort de l','type','3 points']) and len(d['message'].split(' ; '))==6")" = "True" ] \
  && pass "message : les 6 erreurs, séparées par « ; »" || fail "message de invalid_config"
printf '{pas du json' > "$BODY_FILE.inv"
expect "PUT JSON mal formé" "$(call PUT /config/polygons "$TOKEN" "$BODY_FILE.inv")" 400 invalid_request
expect "GET : rien n'a changé après les refus" "$(call GET /config/polygons "$TOKEN")" 200
[ "$(field "d['spots'][1]['id']=='A2' and d['spots'][1]['max_duration_min']==45")" = "True" ] && pass "fichier intact" || fail "fichier modifié par un PUT refusé"

echo "Remise en état"
if [ -s "$ORIGINAL" ]; then
  c=$(call PUT /config/polygons "$TOKEN" "$ORIGINAL")
  if [ "$c" = "200" ]; then
    echo "  fichier de départ remis (PUT : 200)"
  else
    # Ce n'est pas un échec de la route : le fichier de départ lui-même ne respecte pas le contrat.
    echo "  [ATTENTION] le fichier de départ est refusé ($c) : il ne respecte pas le contrat §5.2."
    echo "              $(field "d.get('message','')[:160]")…"
    echo "              Pour remettre les données d'exemple : ./server/scripts/run-mongo.sh server/mongo/seed.js"
  fi
fi
if [ "$LOCAL" = 1 ] && [ -n "$A1_STATE" ]; then
  mongo "const e=$A1_STATE; db.spots.updateOne({_id:\"A1\"},{\$set:{state:e.state,since:e.since?new Date(e.since):null}})" >/dev/null
  echo "  état de A1 remis : $A1_STATE"
fi

rm -f "$RESPONSE" "$BODY_FILE" "$BODY_FILE.inv" "$ORIGINAL"
echo
echo "$passed vérification(s) réussie(s), $failed en échec"
exit $((failed > 0))
