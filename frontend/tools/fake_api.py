#!/usr/bin/env python3
"""Fausse API pour tester les deux frontends sans le vrai serveur.

Sert les fichiers comme Nginx (server/nginx/nginx.conf) :
    /           -> frontend/front-office/   (application conducteur)
    /admin/     -> frontend/back-office/    (application opérateur)
    /shared/    -> frontend/shared/         (code et styles communs)
et implémente, selon le contrat (docs/contrats.md) :
    POST /api/auth/register      -> 201 + user (toujours DRIVER), 400 invalid_request, 409 email_already_exists
    POST /api/auth/login         -> 200 + {access_token, type, expires_in, user}, 401 invalid_credentials
    GET  /api/auth/me            -> 200 + user, 401 unauthenticated
    GET  /api/config/polygons    -> 200 + le dernier places.json enregistré, 404 not_found      (OPERATOR)
    PUT  /api/config/polygons    -> 200 + le fichier enregistré, 400 invalid_config           (OPERATOR)
Les comptes sont gardés en mémoire (perdus à l'arrêt). Deux comptes de test existent au démarrage :
    operateur@exemple.tn / operateur123     (OPERATOR, back office)
    conducteur@exemple.tn / conducteur123   (DRIVER, front office)
Le jeton renvoyé a la forme d'un JWT (sub, role, exp) mais N'EST PAS SIGNÉ.

Jetons de test acceptés en plus (en-tête « Authorization: Bearer <jeton> ») :
    - « operator » : OPERATOR ;  « driver » : DRIVER ;  pas d'en-tête : 401 unauthenticated.
Aucune signature n'est vérifiée : c'est un outil de test, jamais à déployer.

Le fichier enregistré va dans frontend/tools/data/polygons.json (ignoré par Git).

Lancer depuis la racine du dépôt :
    python frontend/tools/fake_api.py              # http://localhost:8000
    python frontend/tools/fake_api.py --port 8001
Puis ouvrir http://localhost:8000/ (conducteur) ou http://localhost:8000/admin/ (opérateur).
"""

import argparse
import base64
import hashlib
import json
import re
import secrets
import sys
import threading
import time
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_spots import validate  # mêmes règles que le serveur  # noqa: E402

FRONTEND_DIR = Path(__file__).resolve().parent.parent
# URL prefix -> folder, in the same order as the locations of server/nginx/nginx.conf
STATIC_ROOTS = [("/admin", FRONTEND_DIR / "back-office"), ("/shared", FRONTEND_DIR / "shared"),
                ("", FRONTEND_DIR / "front-office")]
DEFAULT_FILE = Path(__file__).resolve().parent / "data" / "polygons.json"
POLYGONS_ROUTE = "/api/config/polygons"
# Spots of the plan (contract §4.3).
PLAN_IDS = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]
MAX_BODY = 1_000_000  # bytes

# ---------- Fake authentication (contract §2.5) ----------
TOKEN_LIFETIME_S = 3600
VALID_EMAIL = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
TEST_TOKENS = {"operator": "OPERATOR", "driver": "DRIVER"}
_lock = threading.Lock()
USERS = {}  # email -> {id, name, email, role, active, hash}


def _hash(password):
    return hashlib.sha256(password.encode("utf-8")).hexdigest()  # test tool: no Argon2 here


def create_user(name, email, password, role="DRIVER"):
    u = {"id": "u_" + secrets.token_hex(4), "name": name, "email": email, "role": role,
         "active": True, "hash": _hash(password)}
    USERS[email] = u
    return u


def reset_accounts():
    USERS.clear()
    create_user("Opérateur de test", "operateur@exemple.tn", "operateur123", "OPERATOR")
    create_user("Conducteur de test", "conducteur@exemple.tn", "conducteur123")


reset_accounts()


def public(u):
    return {k: u[k] for k in ("id", "name", "email", "role")}


def _b64(obj):
    return base64.urlsafe_b64encode(json.dumps(obj).encode("utf-8")).rstrip(b"=").decode("ascii")


def make_token(u):
    """JWT-shaped token (header.payload.signature) but NOT signed: test tool only."""
    now = int(time.time())
    payload = {"iss": "fake-api", "sub": u["id"], "upn": u["email"], "role": u["role"],
               "groups": [u["role"]], "iat": now, "exp": now + TOKEN_LIFETIME_S}
    return f"{_b64({'alg': 'none', 'typ': 'JWT'})}.{_b64(payload)}.not-signed"


def read_token(token):
    """Payload if the token comes from the fake API and is not expired, otherwise None."""
    parts = token.split(".")
    if len(parts) != 3:
        return None
    try:
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + "=" * (-len(parts[1]) % 4)))
    except (ValueError, json.JSONDecodeError):
        return None
    if not isinstance(payload, dict) or payload.get("exp", 0) <= time.time():
        return None
    return payload


def token_role(token):
    """Role of a token: a test token (« operator », « driver ») or a token of the fake login. None if invalid."""
    if not token:
        return None
    if token.lower() in TEST_TOKENS:
        return TEST_TOKENS[token.lower()]
    payload = read_token(token)
    return payload.get("role") if payload else None


def validate_contract(data):
    """Rules of the 400 invalid_config error: those of validate_spots.py + spot missing from the plan."""
    errors = validate(data)
    if isinstance(data, dict) and isinstance(data.get("spots"), list):
        for s in data["spots"]:
            if isinstance(s, dict) and isinstance(s.get("id"), str) and s["id"] not in PLAN_IDS:
                errors.append(f"{s['id']} : cette place n'existe pas dans le plan ({', '.join(PLAN_IDS)})")
    return errors


class Handler(SimpleHTTPRequestHandler):
    file = DEFAULT_FILE

    # ---------- Static files: same layout as Nginx ----------
    def translate_path(self, path):
        url_path = urlsplit(path).path
        for prefix, root in STATIC_ROOTS:
            if prefix == "" or url_path == prefix or url_path.startswith(prefix + "/"):
                saved = self.directory
                self.directory = str(root)
                try:
                    return super().translate_path(path[len(prefix):] or "/")
                finally:
                    self.directory = saved
        return super().translate_path(path)

    # ---------- JSON helpers ----------
    def send_json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def error(self, code, error, message):
        self.send_json(code, {"error": error, "message": message})

    def route(self):
        return self.path.split("?", 1)[0]

    def bearer(self):
        header = self.headers.get("Authorization", "")
        return header[7:].strip() if header.startswith("Bearer ") else ""

    def require_role(self, role):
        """True if the call can go on, otherwise answers 401 or 403."""
        actual = token_role(self.bearer())
        if actual is None:
            self.error(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.")
            return False
        if actual != role:
            self.error(403, "forbidden", "Cette page est réservée aux opérateurs.")
            return False
        return True

    def read_json_body(self):
        """JSON body of the request (dict), or None after answering 400."""
        size = int(self.headers.get("Content-Length") or 0)
        if size <= 0 or size > MAX_BODY:
            self.error(400, "invalid_request", "Corps de la requête vide ou trop gros.")
            return None
        try:
            data = json.loads(self.rfile.read(size).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.error(400, "invalid_request", f"JSON mal formé : {e}")
            return None
        if not isinstance(data, dict):
            self.error(400, "invalid_request", "Le corps doit être un objet JSON.")
            return None
        return data

    def invalid_token_present(self):
        """Like MicroProfile JWT: an invalid token is refused with 401, even on a public route."""
        if self.headers.get("Authorization") and token_role(self.bearer()) is None:
            self.error(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.")
            return True
        return False

    # ---------- Routes ----------
    def do_POST(self):
        if self.route() == "/api/auth/register":
            return self.register()
        if self.route() == "/api/auth/login":
            return self.login()
        self.error(404, "not_found", f"Route inconnue de la fausse API : {self.route()}")

    def register(self):
        if self.invalid_token_present():
            return
        d = self.read_json_body()
        if d is None:
            return
        name = str(d.get("name") or "").strip()
        email = str(d.get("email") or "").strip().lower()
        password = d.get("password")
        if not name:
            return self.error(400, "invalid_request", "Le nom est obligatoire.")
        if not VALID_EMAIL.match(email):
            return self.error(400, "invalid_request", "L'adresse email n'est pas valide.")
        if not isinstance(password, str) or not 8 <= len(password) <= 128:
            return self.error(400, "invalid_request", "Le mot de passe doit faire entre 8 et 128 caractères.")
        with _lock:
            if email in USERS:
                return self.error(409, "email_already_exists", "Un compte existe déjà avec cet email.")
            u = create_user(name, email, password)  # always DRIVER: a "role" field is ignored
        self.send_json(201, {**public(u), "active": True})

    def login(self):
        if self.invalid_token_present():
            return
        d = self.read_json_body()
        if d is None:
            return
        email = str(d.get("email") or "").strip().lower()
        u = USERS.get(email)
        if not u or u["hash"] != _hash(str(d.get("password") or "")):
            return self.error(401, "invalid_credentials", "Email ou mot de passe incorrect.")
        if not u["active"]:
            return self.error(403, "account_disabled", "Ce compte est désactivé.")
        self.send_json(200, {"access_token": make_token(u), "type": "Bearer",
                             "expires_in": TOKEN_LIFETIME_S, "user": public(u)})

    def me(self):
        payload = read_token(self.bearer())
        u = next((x for x in USERS.values() if payload and x["id"] == payload.get("sub")), None)
        if not u:
            return self.error(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.")
        self.send_json(200, {**public(u), "active": u["active"], "permissions": []})

    def do_GET(self):
        if not self.route().startswith("/api/"):
            return super().do_GET()  # files of the two applications
        if self.route() == "/api/auth/me":
            return self.me()
        if self.route() != POLYGONS_ROUTE:
            return self.error(404, "not_found", f"Route inconnue de la fausse API : {self.route()}")
        if not self.require_role("OPERATOR"):
            return
        if not self.file.exists():
            return self.error(404, "not_found", "Aucun fichier de polygones enregistré pour l'instant.")
        self.send_json(200, json.loads(self.file.read_text(encoding="utf-8")))

    def do_PUT(self):
        if self.route() != POLYGONS_ROUTE:
            return self.error(404, "not_found", f"Route inconnue de la fausse API : {self.route()}")
        if not self.require_role("OPERATOR"):
            return
        size = int(self.headers.get("Content-Length") or 0)
        if size <= 0 or size > MAX_BODY:
            return self.error(400, "invalid_request", "Corps de la requête vide ou trop gros.")
        try:
            data = json.loads(self.rfile.read(size).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            return self.error(400, "invalid_request", f"JSON mal formé : {e}")
        errors = validate_contract(data)
        if errors:
            return self.error(400, "invalid_config", " ; ".join(errors))
        self.file.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.file.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        tmp.replace(self.file)
        self.log_message("polygons saved: %d spot(s), %d zone(s)", len(data["spots"]), len(data["forbidden_zones"]))
        self.send_json(200, data)

    def end_headers(self):
        # No cache during tests: code changes are visible immediately.
        if not self.route().startswith("/api/"):
            self.send_header("Cache-Control", "no-cache")
        super().end_headers()


def main():
    parser = argparse.ArgumentParser(description="Fausse API des deux frontends (auth et polygones)")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--file", default=str(DEFAULT_FILE), help="où enregistrer places.json")
    args = parser.parse_args()

    Handler.file = Path(args.file).resolve()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Fausse API prête : http://localhost:{args.port}/ (conducteur) · http://localhost:{args.port}/admin/ (opérateur)")
    print(f"Fichier des polygones : {Handler.file}")
    print("Comptes de test : operateur@exemple.tn / operateur123 · conducteur@exemple.tn / conducteur123")
    print("Jetons de test : « operator » (OPERATOR), « driver » (DRIVER), aucun (401). Ctrl+C pour arrêter.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nArrêt.")


if __name__ == "__main__":
    main()
