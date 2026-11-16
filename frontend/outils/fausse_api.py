#!/usr/bin/env python3
"""Fausse API pour tester la PWA sans le vrai serveur (sprint 3).

Sert les fichiers de frontend/ ET implémente, selon le contrat (docs/contrats.md §2.10) :
    GET /api/config/polygones   -> 200 + le dernier places.json enregistré, ou 404 « introuvable »
    PUT /api/config/polygones   -> 200 + le fichier enregistré, ou 400 « config_invalide »

Et l'authentification (contrat §2.5), pour tester la page de connexion :
    POST /api/auth/inscription  -> 201 + utilisateur (toujours CONDUCTEUR), 400 requete_invalide, 409 email_existant
    POST /api/auth/connexion    -> 200 + {jeton, type, expire_dans, utilisateur}, 401 identifiants_invalides
    GET  /api/auth/moi          -> 200 + utilisateur, 401 non_authentifie
Les comptes sont gardés en mémoire (perdus à l'arrêt). Deux comptes de test existent au démarrage :
    exploitant@exemple.tn / exploitant123   (EXPLOITANT)
    conducteur@exemple.tn / conducteur123   (CONDUCTEUR)
Le jeton renvoyé a la forme d'un JWT (sub, role, exp) mais N'EST PAS SIGNÉ.

Authentification simulée (en-tête « Authorization: Bearer <jeton> », contrat §1.2) :
    - pas d'en-tête                                   -> 401 non_authentifie
    - jeton « conducteur », ou JWT dont role = CONDUCTEUR -> 403 acces_refuse
    - tout autre jeton (ex. « exploitant »)            -> accepté comme EXPLOITANT
Aucune signature n'est vérifiée : c'est un outil de test, jamais à déployer.

Le fichier enregistré va dans frontend/outils/donnees/polygones.json (ignoré par Git).

Lancer depuis la racine du dépôt :
    python frontend/outils/fausse_api.py              # http://localhost:8000
    python frontend/outils/fausse_api.py --port 8001
Puis ouvrir http://localhost:8000/calibration.html
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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from valider_places import valider  # mêmes règles que le script de vérification d'Amal  # noqa: E402

DOSSIER_FRONTEND = Path(__file__).resolve().parent.parent
FICHIER_PAR_DEFAUT = Path(__file__).resolve().parent / "donnees" / "polygones.json"
ROUTE = "/api/config/polygones"
# Places du plan (contrat §4.3). Plus tard, le vrai serveur les lira dans GET /api/config/plan.
PLAN_IDS = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]
TAILLE_MAX = 1_000_000  # octets

# ---------- Authentification simulée (contrat §2.5) ----------
DUREE_JETON_S = 3600
EMAIL_VALIDE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
_verrou = threading.Lock()
UTILISATEURS = {}  # email -> {id, nom, email, role, actif, empreinte}


def _empreinte(mot_de_passe):
    return hashlib.sha256(mot_de_passe.encode("utf-8")).hexdigest()  # outil de test : pas de PBKDF2 ici


def creer_utilisateur(nom, email, mot_de_passe, role="CONDUCTEUR"):
    u = {"id": "u_" + secrets.token_hex(4), "nom": nom, "email": email, "role": role,
         "actif": True, "empreinte": _empreinte(mot_de_passe)}
    UTILISATEURS[email] = u
    return u


def reinitialiser_comptes():
    UTILISATEURS.clear()
    creer_utilisateur("Exploitant de test", "exploitant@exemple.tn", "exploitant123", "EXPLOITANT")
    creer_utilisateur("Conducteur de test", "conducteur@exemple.tn", "conducteur123")


reinitialiser_comptes()


def public(u):
    return {k: u[k] for k in ("id", "nom", "email", "role")}


def _b64(objet):
    return base64.urlsafe_b64encode(json.dumps(objet).encode("utf-8")).rstrip(b"=").decode("ascii")


def fabriquer_jeton(u):
    """Jeton au format JWT (en-tête.charge.signature) mais NON signé : outil de test uniquement."""
    maintenant = int(time.time())
    charge = {"iss": "fausse-api", "sub": u["id"], "upn": u["email"], "role": u["role"],
              "groups": [u["role"]], "iat": maintenant, "exp": maintenant + DUREE_JETON_S}
    return f"{_b64({'alg': 'none', 'typ': 'JWT'})}.{_b64(charge)}.non-signe"


def lire_jeton(jeton):
    """Charge utile si le jeton vient de la fausse API et n'est pas expiré, sinon None."""
    morceaux = jeton.split(".")
    if len(morceaux) != 3:
        return None
    try:
        charge = json.loads(base64.urlsafe_b64decode(morceaux[1] + "=" * (-len(morceaux[1]) % 4)))
    except (ValueError, json.JSONDecodeError):
        return None
    if not isinstance(charge, dict) or charge.get("exp", 0) <= time.time():
        return None
    return charge


def valider_contrat(donnees):
    """Règles de l'erreur 400 config_invalide : celles de valider_places.py + place absente du plan."""
    erreurs = valider(donnees)
    if isinstance(donnees, dict) and isinstance(donnees.get("places"), list):
        for p in donnees["places"]:
            if isinstance(p, dict) and isinstance(p.get("id"), str) and p["id"] not in PLAN_IDS:
                erreurs.append(f"{p['id']} : cette place n'existe pas dans le plan ({', '.join(PLAN_IDS)})")
    return erreurs


def role_du_jeton(jeton):
    """Rôle simulé : « conducteur » ou un JWT dont la charge utile contient role = CONDUCTEUR."""
    if jeton.lower() == "conducteur":
        return "CONDUCTEUR"
    morceaux = jeton.split(".")
    if len(morceaux) == 3:
        try:
            charge = morceaux[1] + "=" * (-len(morceaux[1]) % 4)
            return json.loads(base64.urlsafe_b64decode(charge)).get("role", "EXPLOITANT")
        except (ValueError, json.JSONDecodeError):
            pass
    return "EXPLOITANT"


class Gestionnaire(SimpleHTTPRequestHandler):
    fichier = FICHIER_PAR_DEFAUT

    def envoyer_json(self, code, objet):
        corps = json.dumps(objet, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(corps)

    def erreur(self, code, erreur, message):
        self.envoyer_json(code, {"erreur": erreur, "message": message})

    def chemin(self):
        return self.path.split("?", 1)[0]

    def autoriser(self):
        """Renvoie True si l'appel peut continuer (rôle EXPLOITANT), sinon répond 401 ou 403."""
        entete = self.headers.get("Authorization", "")
        if not entete.startswith("Bearer ") or not entete[7:].strip():
            self.erreur(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.")
            return False
        if role_du_jeton(entete[7:].strip()) != "EXPLOITANT":
            self.erreur(403, "acces_refuse", "Cette page est réservée à l'exploitant.")
            return False
        return True

    def lire_corps_json(self):
        """Corps JSON de la requête (dict), ou None après avoir répondu 400."""
        taille = int(self.headers.get("Content-Length") or 0)
        if taille <= 0 or taille > TAILLE_MAX:
            self.erreur(400, "requete_invalide", "Corps de la requête vide ou trop gros.")
            return None
        try:
            donnees = json.loads(self.rfile.read(taille).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            self.erreur(400, "requete_invalide", f"JSON mal formé : {e}")
            return None
        if not isinstance(donnees, dict):
            self.erreur(400, "requete_invalide", "Le corps doit être un objet JSON.")
            return None
        return donnees

    def jeton_invalide_present(self):
        """Comme MicroProfile JWT : un jeton invalide est refusé en 401, même sur une route publique."""
        entete = self.headers.get("Authorization", "")
        if entete and lire_jeton(entete[7:].strip() if entete.startswith("Bearer ") else "") is None:
            self.erreur(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.")
            return True
        return False

    def do_POST(self):
        if self.chemin() == "/api/auth/inscription":
            return self.inscription()
        if self.chemin() == "/api/auth/connexion":
            return self.connexion()
        self.erreur(404, "introuvable", f"Route inconnue de la fausse API : {self.chemin()}")

    def inscription(self):
        if self.jeton_invalide_present():
            return
        d = self.lire_corps_json()
        if d is None:
            return
        nom = str(d.get("nom") or "").strip()
        email = str(d.get("email") or "").strip().lower()
        mdp = d.get("mot_de_passe")
        if not nom:
            return self.erreur(400, "requete_invalide", "Le nom est obligatoire.")
        if not EMAIL_VALIDE.match(email):
            return self.erreur(400, "requete_invalide", "L'adresse email n'est pas valide.")
        if not isinstance(mdp, str) or not 8 <= len(mdp) <= 128:
            return self.erreur(400, "requete_invalide", "Le mot de passe doit faire entre 8 et 128 caractères.")
        with _verrou:
            if email in UTILISATEURS:
                return self.erreur(409, "email_existant", "Un compte existe déjà avec cet email.")
            u = creer_utilisateur(nom, email, mdp)  # rôle toujours CONDUCTEUR : un champ « role » est ignoré
        self.envoyer_json(201, {**public(u), "actif": True})

    def connexion(self):
        if self.jeton_invalide_present():
            return
        d = self.lire_corps_json()
        if d is None:
            return
        email = str(d.get("email") or "").strip().lower()
        u = UTILISATEURS.get(email)
        if not u or u["empreinte"] != _empreinte(str(d.get("mot_de_passe") or "")):
            return self.erreur(401, "identifiants_invalides", "Email ou mot de passe incorrect.")
        if not u["actif"]:
            return self.erreur(403, "compte_desactive", "Ce compte est désactivé.")
        self.envoyer_json(200, {"jeton": fabriquer_jeton(u), "type": "Bearer",
                                "expire_dans": DUREE_JETON_S, "utilisateur": public(u)})

    def moi(self):
        entete = self.headers.get("Authorization", "")
        charge = lire_jeton(entete[7:].strip()) if entete.startswith("Bearer ") else None
        u = next((x for x in UTILISATEURS.values() if charge and x["id"] == charge.get("sub")), None)
        if not u:
            return self.erreur(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.")
        self.envoyer_json(200, {**public(u), "autorisations": []})

    def do_GET(self):
        if not self.chemin().startswith("/api/"):
            return super().do_GET()  # fichiers de la PWA
        if self.chemin() == "/api/auth/moi":
            return self.moi()
        if self.chemin() != ROUTE:
            return self.erreur(404, "introuvable", f"Route inconnue de la fausse API : {self.chemin()}")
        if not self.autoriser():
            return
        if not self.fichier.exists():
            return self.erreur(404, "introuvable", "Aucun fichier de polygones enregistré pour l'instant.")
        self.envoyer_json(200, json.loads(self.fichier.read_text(encoding="utf-8")))

    def do_PUT(self):
        if self.chemin() != ROUTE:
            return self.erreur(404, "introuvable", f"Route inconnue de la fausse API : {self.chemin()}")
        if not self.autoriser():
            return
        taille = int(self.headers.get("Content-Length") or 0)
        if taille <= 0 or taille > TAILLE_MAX:
            return self.erreur(400, "requete_invalide", "Corps de la requête vide ou trop gros.")
        try:
            donnees = json.loads(self.rfile.read(taille).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            return self.erreur(400, "requete_invalide", f"JSON mal formé : {e}")
        erreurs = valider_contrat(donnees)
        if erreurs:
            return self.erreur(400, "config_invalide", " ; ".join(erreurs))
        self.fichier.parent.mkdir(parents=True, exist_ok=True)
        temporaire = self.fichier.with_suffix(".tmp")
        temporaire.write_text(json.dumps(donnees, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        temporaire.replace(self.fichier)
        self.log_message("polygones enregistrés : %d place(s), %d zone(s)",
                         len(donnees["places"]), len(donnees["zones_interdites"]))
        self.envoyer_json(200, donnees)

    def end_headers(self):
        # Pas de cache pendant les tests : on voit tout de suite les modifications du code.
        if not self.chemin().startswith("/api/"):
            self.send_header("Cache-Control", "no-cache")
        super().end_headers()


def main():
    parser = argparse.ArgumentParser(description="Fausse API de la PWA (GET/PUT /api/config/polygones)")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--fichier", default=str(FICHIER_PAR_DEFAUT), help="où enregistrer places.json")
    args = parser.parse_args()

    Gestionnaire.fichier = Path(args.fichier).resolve()
    serveur = ThreadingHTTPServer(("127.0.0.1", args.port),
                                  partial(Gestionnaire, directory=str(DOSSIER_FRONTEND)))
    print(f"Fausse API prête : http://localhost:{args.port}/calibration.html")
    print(f"Fichier des polygones : {Gestionnaire.fichier}")
    print(f"Connexion : http://localhost:{args.port}/connexion.html")
    print("Comptes de test : exploitant@exemple.tn / exploitant123 · conducteur@exemple.tn / conducteur123")
    print("Jetons de test : « exploitant » (accepté), « conducteur » (403), aucun (401). Ctrl+C pour arrêter.")
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        print("\nArrêt.")


if __name__ == "__main__":
    main()
