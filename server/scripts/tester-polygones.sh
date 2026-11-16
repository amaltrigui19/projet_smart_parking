#!/usr/bin/env bash
# Teste GET et PUT /api/config/polygones (contrat §2.10) sur un serveur lancé :
# rôles (401 sans jeton, 403 conducteur), PUT valide puis relecture, 400 config_invalide, JSON mal formé.
# En local, vérifie aussi que le PUT ne touche pas à l'état des places (venant du Pi).
# Le fichier de polygones présent au départ est remis à la fin.
#
# Usage (depuis la racine du dépôt, serveur lancé, données d'exemple chargées : server/mongo/seed.js) :
#   EXPLOITANT_EMAIL=... EXPLOITANT_MOT_DE_PASSE=... ./server/scripts/tester-polygones.sh
#   EXPLOITANT_EMAIL=... EXPLOITANT_MOT_DE_PASSE=... ./server/scripts/tester-polygones.sh https://smartparking-supcom.duckdns.org
# Nécessite curl et python. Code de sortie : 0 si tout passe.
set -uo pipefail
cd "$(dirname "$0")/.."      # dossier server/

: "${EXPLOITANT_EMAIL:?définir EXPLOITANT_EMAIL (compte exploitant, voir server/.env)}"
: "${EXPLOITANT_MOT_DE_PASSE:?définir EXPLOITANT_MOT_DE_PASSE}"
BASE=${1:-https://localhost}
API="$BASE/api"
CURL=(curl -s -m 20)
LOCAL=0
if [ "$BASE" = "https://localhost" ]; then
  LOCAL=1
  [ -f certs/ca.pem ] && CURL+=(--cacert certs/ca.pem)
fi
curl --version | grep -q Schannel && CURL+=(--ssl-no-revoke)   # curl de Windows et CA locale
REP=$(mktemp); FICHIER=$(mktemp); ORIGINAL=$(mktemp)
ok=0; ko=0

appel() {  # METHODE CHEMIN JETON [fichier du corps] -> code HTTP ; réponse dans $REP
  local m=$1 p=$2 j=$3 f=${4:-}
  local args=(-o "$REP" -w "%{http_code}" -X "$m" "$API$p" -H "Content-Type: application/json")
  [ -n "$j" ] && args+=(-H "Authorization: Bearer $j")
  [ -n "$f" ] && args+=(--data-binary "@$f")
  env -u MSYS_NO_PATHCONV "${CURL[@]}" "${args[@]}"
}
py() { python -c "$1" "${@:2}"; }
champ() { py "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(eval(sys.argv[2]))" "$REP" "$1" 2>/dev/null; }
reussi() { ok=$((ok+1)); echo "  [OK]    $1"; }
echec() { ko=$((ko+1)); echo "  [ECHEC] $1 $(head -c 300 "$REP")"; }
verifier() {  # nom code attendu [erreur attendue]
  local e; e=$(champ "d.get('erreur','') if isinstance(d,dict) else ''")
  if [ "$2" = "$3" ] && { [ -z "${4:-}" ] || [ "$e" = "$4" ]; }; then reussi "$1 : $2${4:+ $4}"; else echec "$1 : attendu $3${4:+ $4}, obtenu $2"; fi
}
mongo() {  # requête mongosh sur la base smartparking (local seulement)
  docker compose exec -T mongo sh -c "mongosh --quiet -u \"\$MONGO_INITDB_ROOT_USERNAME\" -p \"\$MONGO_INITDB_ROOT_PASSWORD\" --authenticationDatabase admin smartparking --eval '$1'" 2>/dev/null | tr -d '\r'
}
connexion() {  # email mot_de_passe -> jeton
  py "import json,sys;print(json.dumps({'email':sys.argv[1],'mot_de_passe':sys.argv[2]}))" "$1" "$2" > "$FICHIER"
  [ "$(appel POST /auth/connexion '' "$FICHIER")" = "200" ] && champ "d['jeton']"
}

echo "Serveur : $API"
JETON=$(connexion "$EXPLOITANT_EMAIL" "$EXPLOITANT_MOT_DE_PASSE")
[ -n "$JETON" ] || { echo "Connexion exploitant impossible : vérifier EXPLOITANT_EMAIL et EXPLOITANT_MOT_DE_PASSE."; exit 1; }
CONDUCTEUR="test.polygones.$(date +%s).$RANDOM@exemple.tn"
py "import json,sys;print(json.dumps({'nom':'Test','email':sys.argv[1],'mot_de_passe':'motdepasse8'}))" "$CONDUCTEUR" > "$FICHIER"
appel POST /auth/inscription '' "$FICHIER" >/dev/null
JETON_C=$(connexion "$CONDUCTEUR" motdepasse8)

echo "Rôles"
verifier "GET sans jeton" "$(appel GET /config/polygones '')" 401 non_authentifie
verifier "GET par un conducteur" "$(appel GET /config/polygones "$JETON_C")" 403 acces_refuse
verifier "PUT par un conducteur" "$(appel PUT /config/polygones "$JETON_C" "$FICHIER")" 403 acces_refuse

echo "Lecture de départ"
c=$(appel GET /config/polygones "$JETON")
if [ "$c" = "200" ]; then cp "$REP" "$ORIGINAL"; reussi "GET exploitant : 200 ($(champ "len(d['places'])") place(s) enregistrée(s))"
elif [ "$c" = "404" ]; then : > "$ORIGINAL"; reussi "GET exploitant : 404 (rien d'enregistré)"
else echec "GET exploitant : $c"; fi

ETAT_A1=""
if [ "$LOCAL" = 1 ]; then
  ETAT_A1=$(mongo 'print(JSON.stringify(db.places.findOne({_id:"A1"},{etat:1,depuis:1,_id:0})))')
  mongo 'db.places.updateOne({_id:"A1"},{$set:{etat:"occupee",depuis:new Date("2026-11-20T10:02:11Z")}})' >/dev/null
fi

echo "Enregistrement valide"
# 8 places du plan (rectangles), A2 à 45 min, une zone : un fichier conforme au contrat §5.2
py "
import json
def rect(x,y,l=64,h=104): return [[x,y],[x+l,y],[x+l,y+h],[x,y+h]]
plan=[('A1','pmr',120),('A2','normale',45),('A3','normale',120),('A4','normale',120),
      ('B1','normale',120),('B2','normale',120),('B3','normale',120),('B4','electrique',60)]
places=[{'id':i,'type':t,'duree_max_min':d,'poly':rect(112+64*(k%4),0 if k<4 else 216)} for k,(i,t,d) in enumerate(plan)]
print(json.dumps({'parking':'P1','resolution':[640,480],'places':places,
                  'zones_interdites':[{'id':'allee1','poly':rect(400,104,239,112)}]}))" > "$FICHIER"
verifier "PUT valide" "$(appel PUT /config/polygones "$JETON" "$FICHIER")" 200
verifier "GET après PUT" "$(appel GET /config/polygones "$JETON")" 200
[ "$(champ "[p['id'] for p in d['places']]==['A1','A2','A3','A4','B1','B2','B3','B4'] and d['places'][1]['duree_max_min']==45 and d['zones_interdites'][0]['poly'][1]==[639,104] and d['resolution']==[640,480]")" = "True" ] \
  && reussi "relecture conforme (8 places, A2 = 45 min, allee1 modifiée)" || echec "relecture"
[ "$(champ "sorted(d)==['parking','places','resolution','zones_interdites'] and sorted(d['places'][0])==['duree_max_min','id','poly','type']")" = "True" ] \
  && reussi "format du contrat §5.2 (aucun champ en plus)" || echec "format"
if [ "$LOCAL" = 1 ]; then
  [ "$(mongo 'print(db.places.findOne({_id:"A1"}).etat)')" = "occupee" ] && reussi "état de A1 (venant du Pi) conservé : occupee" || echec "état de A1 effacé"
  [ "$(mongo 'print(JSON.stringify(db.parkings.findOne({_id:"P1"}).zones_interdites[0].rect_cm))')" != "null" ] \
    && reussi "rect_cm de allee1 (plan) conservé" || echec "rect_cm de allee1 perdu"
fi

echo "Erreurs"
py "
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
d['places'][1]['id']='A1'                      # identifiant en double
d['places'][2]['duree_max_min']=0              # durée nulle
d['places'][3]['id']='P9'                      # absente du plan
d['places'][4]['poly'][1]=[640,0]              # hors de la résolution
d['places'][5]['type']='reservee'              # type inconnu
d['places'][6]['poly']=d['places'][6]['poly'][:2]  # moins de 3 points
print(json.dumps(d))" "$FICHIER" > "$FICHIER.inv"
verifier "PUT invalide" "$(appel PUT /config/polygones "$JETON" "$FICHIER.inv")" 400 config_invalide
[ "$(champ "all(t in d['message'] for t in ['en double','duree_max_min','P9','sort de l','type','3 points']) and len(d['message'].split(' ; '))==6")" = "True" ] \
  && reussi "message : les 6 erreurs, séparées par « ; »" || echec "message de config_invalide"
printf '{pas du json' > "$FICHIER.inv"
verifier "PUT JSON mal formé" "$(appel PUT /config/polygones "$JETON" "$FICHIER.inv")" 400 requete_invalide
verifier "GET : rien n'a changé après les refus" "$(appel GET /config/polygones "$JETON")" 200
[ "$(champ "d['places'][1]['id']=='A2' and d['places'][1]['duree_max_min']==45")" = "True" ] && reussi "fichier intact" || echec "fichier modifié par un PUT refusé"

echo "Remise en état"
if [ -s "$ORIGINAL" ]; then
  c=$(appel PUT /config/polygones "$JETON" "$ORIGINAL")
  if [ "$c" = "200" ]; then
    echo "  fichier de départ remis (PUT : 200)"
  else
    # Ce n'est pas un échec de la route : le fichier de départ lui-même ne respecte pas le contrat.
    echo "  [ATTENTION] le fichier de départ est refusé ($c) : il ne respecte pas le contrat §5.2."
    echo "              $(champ "d.get('message','')[:160]")…"
    echo "              Pour remettre les données d'exemple : ./server/scripts/executer-mongo.sh server/mongo/seed.js"
  fi
fi
if [ "$LOCAL" = 1 ] && [ -n "$ETAT_A1" ]; then
  mongo "const e=$ETAT_A1; db.places.updateOne({_id:\"A1\"},{\$set:{etat:e.etat,depuis:e.depuis?new Date(e.depuis):null}})" >/dev/null
  echo "  état de A1 remis : $ETAT_A1"
fi

rm -f "$REP" "$FICHIER" "$FICHIER.inv" "$ORIGINAL"
echo
echo "$ok vérification(s) réussie(s), $ko en échec"
exit $((ko > 0))
