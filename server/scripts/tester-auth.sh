#!/usr/bin/env bash
# Teste les routes d'authentification (contrat §2.5) sur un serveur lancé : codes HTTP, format
# {erreur, message}, contenu du JWT. Crée un compte conducteur de test (email aléatoire).
#
# Usage (depuis la racine du dépôt, serveur lancé avec docker compose) :
#   ./server/scripts/tester-auth.sh                              # https://localhost, CA locale
#   ./server/scripts/tester-auth.sh https://smartparking-supcom.duckdns.org
# Pour tester aussi le compte exploitant de démo, définir EXPLOITANT_EMAIL et EXPLOITANT_MOT_DE_PASSE
# dans l'environnement (les mêmes que dans server/.env).
#
# Nécessite curl et python (pour lire le JSON). Code de sortie : 0 si tout passe.
set -uo pipefail
cd "$(dirname "$0")/.."      # dossier server/

BASE=${1:-https://localhost}
API="$BASE/api"
CURL=(curl -s -m 20)
[ "$BASE" = "https://localhost" ] && [ -f certs/ca.pem ] && CURL+=(--cacert certs/ca.pem)
curl --version | grep -q Schannel && CURL+=(--ssl-no-revoke)   # curl de Windows et CA locale
REP=$(mktemp)
ok=0; ko=0

# appel METHODE CHEMIN [CORPS] [en-têtes curl…] -> affiche le code HTTP, corps dans $REP
appel() {
  local m=$1 p=$2 d=${3:-}; shift 3 2>/dev/null || shift $#
  env -u MSYS_NO_PATHCONV "${CURL[@]}" -o "$REP" -w "%{http_code}" -X "$m" "$API$p" \
    -H "Content-Type: application/json" ${d:+--data-raw "$d"} "$@"
}
champ() { python -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(eval(sys.argv[2]))" "$REP" "$1" 2>/dev/null; }
verifier() {  # nom code_obtenu code_attendu [code_erreur_attendu]
  local nom=$1 obtenu=$2 attendu=$3 erreur=${4:-}
  local e; e=$(champ "d.get('erreur','')")
  if [ "$obtenu" = "$attendu" ] && { [ -z "$erreur" ] || [ "$e" = "$erreur" ]; }; then
    ok=$((ok+1)); echo "  [OK]    $nom : $obtenu${erreur:+ $erreur}"
  else
    ko=$((ko+1)); echo "  [ECHEC] $nom : attendu $attendu${erreur:+ $erreur}, obtenu $obtenu $(head -c 200 "$REP")"
  fi
}

EMAIL="test.$(date +%s).$RANDOM@exemple.tn"
MDP="motdepasse8"
echo "Serveur : $API · compte de test : $EMAIL"

echo "Inscription"
c=$(appel POST /auth/inscription "{\"nom\":\"Test Auth\",\"email\":\"$EMAIL\",\"mot_de_passe\":\"$MDP\",\"role\":\"EXPLOITANT\"}")
verifier "inscription" "$c" 201
[ "$(champ "d['role']")" = "CONDUCTEUR" ] && { ok=$((ok+1)); echo "  [OK]    champ role envoyé ignoré : CONDUCTEUR"; } || { ko=$((ko+1)); echo "  [ECHEC] rôle : $(cat "$REP")"; }
verifier "email en double (majuscules)" "$(appel POST /auth/inscription "{\"nom\":\"X\",\"email\":\"${EMAIL^^}\",\"mot_de_passe\":\"$MDP\"}")" 409 email_existant
verifier "email invalide" "$(appel POST /auth/inscription '{"nom":"X","email":"pas-un-email","mot_de_passe":"motdepasse8"}')" 400 requete_invalide
verifier "mot de passe < 8" "$(appel POST /auth/inscription '{"nom":"X","email":"x@exemple.tn","mot_de_passe":"court"}')" 400 requete_invalide
verifier "JSON mal formé" "$(appel POST /auth/inscription '{pas du json')" 400 requete_invalide

echo "Connexion"
verifier "connexion" "$(appel POST /auth/connexion "{\"email\":\"$EMAIL\",\"mot_de_passe\":\"$MDP\"}")" 200
JETON=$(champ "d['jeton']")
FORME=$(champ "sorted(d) == ['expire_dans','jeton','type','utilisateur'] and d['type']=='Bearer' and d['expire_dans']==3600 and sorted(d['utilisateur'])==['email','id','nom','role']")
[ "$FORME" = "True" ] && { ok=$((ok+1)); echo "  [OK]    réponse : jeton, type Bearer, expire_dans 3600, utilisateur"; } || { ko=$((ko+1)); echo "  [ECHEC] réponse de connexion : $(cat "$REP")"; }
REVEND=$(python -c "import json,base64,sys;p=sys.argv[1].split('.')[1];p+='='*(-len(p)%4);c=json.loads(base64.urlsafe_b64decode(p));print(c['iss']=='smartparking' and c['role']=='CONDUCTEUR' and c['groups']==['CONDUCTEUR'] and c['sub'].startswith('u_') and c['exp']-c['iat']==3600)" "$JETON" 2>/dev/null)
[ "$REVEND" = "True" ] && { ok=$((ok+1)); echo "  [OK]    JWT : iss, sub u_…, role, groups, exp = iat + 1 h"; } || { ko=$((ko+1)); echo "  [ECHEC] contenu du JWT"; }
verifier "mauvais mot de passe" "$(appel POST /auth/connexion "{\"email\":\"$EMAIL\",\"mot_de_passe\":\"mauvais123\"}")" 401 identifiants_invalides
M1=$(champ "d['message']")
verifier "email inconnu" "$(appel POST /auth/connexion '{"email":"inconnu@exemple.tn","mot_de_passe":"motdepasse8"}')" 401 identifiants_invalides
[ "$M1" = "$(champ "d['message']")" ] && { ok=$((ok+1)); echo "  [OK]    même message pour email inconnu et mauvais mot de passe"; } || { ko=$((ko+1)); echo "  [ECHEC] messages différents"; }

echo "Moi"
verifier "moi avec jeton" "$(appel GET /auth/moi '' -H "Authorization: Bearer $JETON")" 200
verifier "moi sans jeton" "$(appel GET /auth/moi '')" 401 non_authentifie
verifier "moi jeton bidon" "$(appel GET /auth/moi '' -H 'Authorization: Bearer abc.def.ghi')" 401 non_authentifie
FAUX=$(python -c "import json,base64,sys;h,p,s=sys.argv[1].split('.');q=p+'='*(-len(p)%4);c=json.loads(base64.urlsafe_b64decode(q));c['role']='EXPLOITANT';c['groups']=['EXPLOITANT'];print(h+'.'+base64.urlsafe_b64encode(json.dumps(c).encode()).decode().rstrip('=')+'.'+s)" "$JETON")
verifier "moi jeton falsifié (rôle changé)" "$(appel GET /auth/moi '' -H "Authorization: Bearer $FAUX")" 401 non_authentifie
if [ -r jwt/privee.pem ]; then
  # Jeton bien signé avec la vraie clé, mais expiré depuis 1 heure
  b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
  SUB=$(python -c "import json,base64,sys;p=sys.argv[1].split('.')[1];p+='='*(-len(p)%4);print(json.loads(base64.urlsafe_b64decode(p))['sub'])" "$JETON")
  T=$(( $(date +%s) - 7200 ))
  H=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  P=$(printf '{"iss":"smartparking","sub":"%s","upn":"%s","role":"CONDUCTEUR","groups":["CONDUCTEUR"],"iat":%d,"exp":%d}' "$SUB" "$EMAIL" "$T" "$((T + 3600))" | b64url)
  S=$(printf '%s' "$H.$P" | openssl dgst -sha256 -sign jwt/privee.pem -binary | b64url)
  verifier "moi jeton expiré (bien signé)" "$(appel GET /auth/moi '' -H "Authorization: Bearer $H.$P.$S")" 401 non_authentifie
fi

if [ -n "${EXPLOITANT_EMAIL:-}" ] && [ -n "${EXPLOITANT_MOT_DE_PASSE:-}" ]; then
  echo "Compte exploitant de démo"
  verifier "connexion exploitant" "$(appel POST /auth/connexion "{\"email\":\"$EXPLOITANT_EMAIL\",\"mot_de_passe\":\"$EXPLOITANT_MOT_DE_PASSE\"}")" 200
  [ "$(champ "d['utilisateur']['role']")" = "EXPLOITANT" ] && { ok=$((ok+1)); echo "  [OK]    rôle EXPLOITANT"; } || { ko=$((ko+1)); echo "  [ECHEC] rôle exploitant : $(cat "$REP")"; }
fi

rm -f "$REP"
echo
echo "$ok vérification(s) réussie(s), $ko en échec"
exit $((ko > 0))
