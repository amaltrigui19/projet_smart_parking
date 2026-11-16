"""Tests de la fausse API (fausse_api.py) : polygones (contrat §2.10) et authentification (§2.5).

Lancer depuis la racine du dépôt :
    python -m unittest frontend/outils/test_fausse_api.py -v
"""

import json
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fausse_api  # noqa: E402

VALIDE = {
    "parking": "P1",
    "resolution": [640, 480],
    "places": [
        {"id": "A1", "type": "pmr", "duree_max_min": 120, "poly": [[40, 60], [130, 60], [130, 200], [40, 200]]},
        {"id": "B4", "type": "electrique", "duree_max_min": 60, "poly": [[340, 280], [430, 280], [430, 420], [340, 420]]},
    ],
    "zones_interdites": [{"id": "allee1", "poly": [[500, 210], [639, 210], [639, 280], [500, 280]]}],
}


class TestFausseApi(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dossier = tempfile.TemporaryDirectory()
        fausse_api.Gestionnaire.fichier = Path(cls.dossier.name) / "polygones.json"
        fausse_api.Gestionnaire.log_message = lambda *a: None  # tests silencieux
        cls.serveur = ThreadingHTTPServer(
            ("127.0.0.1", 0), partial(fausse_api.Gestionnaire, directory=str(fausse_api.DOSSIER_FRONTEND)))
        cls.base = f"http://127.0.0.1:{cls.serveur.server_address[1]}"
        threading.Thread(target=cls.serveur.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.serveur.shutdown()
        cls.serveur.server_close()
        cls.dossier.cleanup()

    def appel(self, methode, chemin, corps=None, jeton="exploitant", brut=None):
        donnees = brut if brut is not None else (json.dumps(corps).encode() if corps is not None else None)
        req = urllib.request.Request(self.base + chemin, data=donnees, method=methode)
        if jeton:
            req.add_header("Authorization", f"Bearer {jeton}")
        if donnees is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def test_1_rien_enregistre_404(self):
        code, corps = self.appel("GET", "/api/config/polygones")
        self.assertEqual(code, 404)
        self.assertEqual(json.loads(corps)["erreur"], "introuvable")

    def test_2_sans_jeton_401(self):
        code, corps = self.appel("PUT", "/api/config/polygones", VALIDE, jeton=None)
        self.assertEqual((code, json.loads(corps)["erreur"]), (401, "non_authentifie"))

    def test_3_conducteur_403(self):
        code, corps = self.appel("PUT", "/api/config/polygones", VALIDE, jeton="conducteur")
        self.assertEqual((code, json.loads(corps)["erreur"]), (403, "acces_refuse"))

    def test_4_config_invalide_400(self):
        invalide = json.loads(json.dumps(VALIDE))
        invalide["places"][1]["id"] = "A1"            # identifiant en double
        invalide["places"][0]["duree_max_min"] = 0     # durée nulle
        code, corps = self.appel("PUT", "/api/config/polygones", invalide)
        reponse = json.loads(corps)
        self.assertEqual((code, reponse["erreur"]), (400, "config_invalide"))
        self.assertIn("en double", reponse["message"])
        self.assertIn("duree_max_min", reponse["message"])

    def test_5_place_hors_plan_400(self):
        invalide = json.loads(json.dumps(VALIDE))
        invalide["places"][0]["id"] = "P9"
        code, corps = self.appel("PUT", "/api/config/polygones", invalide)
        self.assertEqual(code, 400)
        self.assertIn("n'existe pas dans le plan", json.loads(corps)["message"])

    def test_6_json_mal_forme_400(self):
        code, corps = self.appel("PUT", "/api/config/polygones", brut=b"{pas du json")
        self.assertEqual((code, json.loads(corps)["erreur"]), (400, "requete_invalide"))

    def test_7_enregistrer_puis_relire(self):
        code, corps = self.appel("PUT", "/api/config/polygones", VALIDE)
        self.assertEqual((code, json.loads(corps)), (200, VALIDE))
        code, corps = self.appel("GET", "/api/config/polygones")
        self.assertEqual((code, json.loads(corps)), (200, VALIDE))

    def test_8_sert_la_pwa(self):
        code, corps = self.appel("GET", "/calibration.html", jeton=None)
        self.assertEqual(code, 200)
        self.assertIn(b"Calibration des places", corps)

    # ---------- Authentification (contrat §2.5) ----------
    def connecter(self, email, mdp):
        code, corps = self.appel("POST", "/api/auth/connexion", {"email": email, "mot_de_passe": mdp}, jeton=None)
        return code, json.loads(corps)

    def test_a_connexion_exploitant(self):
        code, r = self.connecter("exploitant@exemple.tn", "exploitant123")
        self.assertEqual(code, 200)
        self.assertEqual((r["type"], r["expire_dans"], r["utilisateur"]["role"]), ("Bearer", 3600, "EXPLOITANT"))
        # Le jeton obtenu ouvre /moi et les routes de l'exploitant.
        code, corps = self.appel("GET", "/api/auth/moi", jeton=r["jeton"])
        self.assertEqual((code, json.loads(corps)["email"]), (200, "exploitant@exemple.tn"))
        code, _ = self.appel("PUT", "/api/config/polygones", VALIDE, jeton=r["jeton"])
        self.assertEqual(code, 200)

    def test_b_connexion_conducteur_refuse_polygones(self):
        code, r = self.connecter("conducteur@exemple.tn", "conducteur123")
        self.assertEqual((code, r["utilisateur"]["role"]), (200, "CONDUCTEUR"))
        code, corps = self.appel("PUT", "/api/config/polygones", VALIDE, jeton=r["jeton"])
        self.assertEqual((code, json.loads(corps)["erreur"]), (403, "acces_refuse"))

    def test_c_mauvais_mot_de_passe_401(self):
        code, r = self.connecter("exploitant@exemple.tn", "mauvais-mdp")
        self.assertEqual((code, r["erreur"]), (401, "identifiants_invalides"))
        code2, r2 = self.connecter("inconnu@exemple.tn", "mauvais-mdp")
        self.assertEqual((code2, r2["message"]), (401, r["message"]))  # même message dans les deux cas

    def test_d_inscription_puis_connexion(self):
        corps = {"nom": "Sara Ben Ali", "email": "Sara@Exemple.tn", "mot_de_passe": "motdepasse8", "role": "EXPLOITANT"}
        code, r = self.appel("POST", "/api/auth/inscription", corps, jeton=None)
        r = json.loads(r)
        self.assertEqual((code, r["role"], r["email"]), (201, "CONDUCTEUR", "sara@exemple.tn"))  # rôle ignoré
        code, _ = self.appel("POST", "/api/auth/inscription", corps, jeton=None)
        self.assertEqual(code, 409)
        code, r = self.connecter("sara@exemple.tn", "motdepasse8")
        self.assertEqual((code, r["utilisateur"]["nom"]), (200, "Sara Ben Ali"))

    def test_e_inscription_invalide_400(self):
        code, corps = self.appel("POST", "/api/auth/inscription",
                                 {"nom": "X", "email": "x@exemple.tn", "mot_de_passe": "court"}, jeton=None)
        self.assertEqual((code, json.loads(corps)["erreur"]), (400, "requete_invalide"))

    def test_f_moi_sans_jeton_ou_jeton_bidon_401(self):
        for jeton in (None, "bidon", "a.b.c"):
            code, corps = self.appel("GET", "/api/auth/moi", jeton=jeton)
            self.assertEqual((code, json.loads(corps)["erreur"]), (401, "non_authentifie"))


if __name__ == "__main__":
    unittest.main()
