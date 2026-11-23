#!/usr/bin/env bash
# Teste l'IAM sur un serveur lancé (contrat §2.2 et §2.5) : routes register, login, me, JWKS,
# codes HTTP, format {error, message}, contenu et signature du JWT. Crée un compte DRIVER de test
# (email aléatoire).
#
# Usage (depuis la racine du dépôt, serveur lancé avec docker compose) :
#   ./server/scripts/test-auth.sh                              # https://localhost, CA locale
#   ./server/scripts/test-auth.sh https://smartparking-supcom.duckdns.org
# Pour tester aussi le compte OPERATOR de démo, définir OPERATOR_EMAIL et OPERATOR_PASSWORD
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
RESPONSE=$(mktemp)
passed=0; failed=0

# call METHOD PATH [BODY] [curl headers…] -> prints the HTTP code, body in $RESPONSE
call() {
  local m=$1 p=$2 d=${3:-}; shift 3 2>/dev/null || shift $#
  env -u MSYS_NO_PATHCONV "${CURL[@]}" -o "$RESPONSE" -w "%{http_code}" -X "$m" "$API$p" \
    -H "Content-Type: application/json" ${d:+--data-raw "$d"} "$@"
}
field() { python -c "import json,sys;d=json.load(open(sys.argv[1],encoding='utf-8'));print(eval(sys.argv[2]))" "$RESPONSE" "$1" 2>/dev/null; }
pass() { passed=$((passed+1)); echo "  [OK]    $1"; }
fail() { failed=$((failed+1)); echo "  [ECHEC] $1"; }
expect() {  # name actual_code expected_code [expected_error]
  local name=$1 actual=$2 expected=$3 error=${4:-}
  local e; e=$(field "d.get('error','')")
  if [ "$actual" = "$expected" ] && { [ -z "$error" ] || [ "$e" = "$error" ]; }; then
    pass "$name : $actual${error:+ $error}"
  else
    fail "$name : attendu $expected${error:+ $error}, obtenu $actual $(head -c 200 "$RESPONSE")"
  fi
}
# Décode une partie (0 = en-tête, 1 = contenu) d'un JWT et évalue une expression Python sur c
jwt_part() { python -c "import json,base64,sys;p=sys.argv[1].split('.')[int(sys.argv[2])];p+='='*(-len(p)%4);c=json.loads(base64.urlsafe_b64decode(p));print(eval(sys.argv[3]))" "$1" "$2" "$3" 2>/dev/null; }

EMAIL="test.$(date +%s).$RANDOM@exemple.tn"
PASSWORD="motdepasse8"
echo "Serveur : $API · compte de test : $EMAIL"

echo "JWKS"
code=$(env -u MSYS_NO_PATHCONV "${CURL[@]}" -o "$RESPONSE" -w "%{http_code}" "$BASE/.well-known/jwks.json")
expect "GET /.well-known/jwks.json" "$code" 200
[ "$(field "len(d['keys'])>0 and all(k['kty']=='RSA' and k['alg']=='RS256' and k['use']=='sig' and k.get('kid') and 'd' not in k for k in d['keys'])")" = "True" ] \
  && pass "clés RSA publiques avec kid, sans partie privée" || fail "contenu du JWKS : $(head -c 300 "$RESPONSE")"
KIDS=$(field "' '.join(k['kid'] for k in d['keys'])")

echo "Inscription"
c=$(call POST /auth/register "{\"name\":\"Test Auth\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"OPERATOR\"}")
expect "inscription" "$c" 201
[ "$(field "d['role']")" = "DRIVER" ] && pass "champ role envoyé ignoré : DRIVER" || fail "rôle : $(cat "$RESPONSE")"
expect "email en double (majuscules)" "$(call POST /auth/register "{\"name\":\"X\",\"email\":\"${EMAIL^^}\",\"password\":\"$PASSWORD\"}")" 409 email_already_exists
expect "email invalide" "$(call POST /auth/register '{"name":"X","email":"pas-un-email","password":"motdepasse8"}')" 400 invalid_request
expect "mot de passe < 8" "$(call POST /auth/register '{"name":"X","email":"x@exemple.tn","password":"court"}')" 400 invalid_request
expect "JSON mal formé" "$(call POST /auth/register '{pas du json')" 400 invalid_request

echo "Connexion"
expect "connexion" "$(call POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")" 200
TOKEN=$(field "d['access_token']")
SHAPE=$(field "{'access_token','type','expires_in','user'} <= set(d) and d['type']=='Bearer' and d['expires_in']==3600 and {'id','name','email','role'} <= set(d['user'])")
[ "$SHAPE" = "True" ] && pass "réponse : access_token, type Bearer, expires_in 3600, user" || fail "réponse de connexion : $(cat "$RESPONSE")"
CLAIMS=$(jwt_part "$TOKEN" 1 "c['iss']=='smartparking' and c['role']=='DRIVER' and c['groups']==['DRIVER'] and c['sub'].startswith('u_') and c['exp']-c['iat']==3600")
[ "$CLAIMS" = "True" ] && pass "JWT : iss, sub u_…, role, groups, exp = iat + 1 h" || fail "contenu du JWT"
KID=$(jwt_part "$TOKEN" 0 "c['alg']+' '+c['kid']")
[[ " $KIDS " == *" ${KID#RS256 } "* && "$KID" == RS256\ * ]] && pass "en-tête : RS256, kid publié dans le JWKS (${KID#RS256 })" || fail "en-tête du JWT : $KID"
expect "mauvais mot de passe" "$(call POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"mauvais123\"}")" 401 invalid_credentials
M1=$(field "d['message']")
expect "email inconnu" "$(call POST /auth/login '{"email":"inconnu@exemple.tn","password":"motdepasse8"}')" 401 invalid_credentials
[ "$M1" = "$(field "d['message']")" ] && pass "même message pour email inconnu et mauvais mot de passe" || fail "messages différents"

echo "Profil (me)"
expect "me avec jeton" "$(call GET /auth/me '' -H "Authorization: Bearer $TOKEN")" 200
expect "me sans jeton" "$(call GET /auth/me '')" 401 unauthenticated
expect "me jeton bidon" "$(call GET /auth/me '' -H 'Authorization: Bearer abc.def.ghi')" 401 unauthenticated
FORGED=$(python -c "import json,base64,sys;h,p,s=sys.argv[1].split('.');q=p+'='*(-len(p)%4);c=json.loads(base64.urlsafe_b64decode(q));c['role']='OPERATOR';c['groups']=['OPERATOR'];print(h+'.'+base64.urlsafe_b64encode(json.dumps(c).encode()).decode().rstrip('=')+'.'+s)" "$TOKEN")
expect "me jeton falsifié (rôle changé)" "$(call GET /auth/me '' -H "Authorization: Bearer $FORGED")" 401 unauthenticated

# Jeton bien signé avec la vraie clé (en local : server/jwt/keys/<kid>.pem), mais expiré depuis 1 heure
KEY_FILE="jwt/keys/${KID#RS256 }.pem"
if [ -r "$KEY_FILE" ] && grep -q "PRIVATE KEY" "$KEY_FILE"; then
  b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
  SUB=$(jwt_part "$TOKEN" 1 "c['sub']")
  T=$(( $(date +%s) - 7200 ))
  H=$(printf '{"alg":"RS256","typ":"JWT","kid":"%s"}' "${KID#RS256 }" | b64url)
  P=$(printf '{"iss":"smartparking","sub":"%s","upn":"%s","role":"DRIVER","groups":["DRIVER"],"iat":%d,"exp":%d}' "$SUB" "$EMAIL" "$T" "$((T + 3600))" | b64url)
  S=$(printf '%s' "$H.$P" | openssl dgst -sha256 -sign "$KEY_FILE" -binary | b64url)
  expect "me jeton expiré (bien signé)" "$(call GET /auth/me '' -H "Authorization: Bearer $H.$P.$S")" 401 unauthenticated
fi

if [ -n "${OPERATOR_EMAIL:-}" ] && [ -n "${OPERATOR_PASSWORD:-}" ]; then
  echo "Compte OPERATOR de démo"
  expect "connexion opérateur" "$(call POST /auth/login "{\"email\":\"$OPERATOR_EMAIL\",\"password\":\"$OPERATOR_PASSWORD\"}")" 200
  [ "$(field "d['user']['role']")" = "OPERATOR" ] && pass "rôle OPERATOR" || fail "rôle opérateur : $(cat "$RESPONSE")"
fi

rm -f "$RESPONSE"
echo
echo "$passed vérification(s) réussie(s), $failed en échec"
exit $((failed > 0))
