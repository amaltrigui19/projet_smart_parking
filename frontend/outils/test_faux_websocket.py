"""Tests du faux serveur temps réel (faux_websocket.py) : GET /api/places (§2.6) et WebSocket (§1.4).

Lancer depuis la racine du dépôt :
    python -m unittest frontend/outils/test_faux_websocket.py -v
"""

import base64
import json
import os
import socket
import sys
import threading
import unittest
import urllib.error
import urllib.request
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import faux_websocket as fw  # noqa: E402


def ouvrir_websocket(port, jeton):
    """Poignée de main à la main. Renvoie (socket, ligne de statut, en-têtes, clé envoyée)."""
    cle = base64.b64encode(os.urandom(16)).decode()
    s = socket.create_connection(("127.0.0.1", port), timeout=5)
    chemin = "/api/ws/places" + (f"?token={jeton}" if jeton else "")
    s.sendall((f"GET {chemin} HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
               f"Sec-WebSocket-Key: {cle}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    flux = s.makefile("rb")
    statut = flux.readline().decode().strip()
    entetes = {}
    while (ligne := flux.readline().decode().strip()):
        nom, _, valeur = ligne.partition(":")
        entetes[nom.strip().lower()] = valeur.strip()
    return s, flux, statut, entetes, cle


def trame_client(opcode, donnees=b""):
    """Trame client -> serveur, masquée comme l'exige la RFC 6455."""
    masque = os.urandom(4)
    return bytes([0x80 | opcode, 0x80 | len(donnees)]) + masque + bytes(o ^ masque[i % 4] for i, o in enumerate(donnees))


class TestFauxWebSocket(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fw.GestionnaireTempsReel.parking = fw.Parking(graine=1)
        fw.GestionnaireTempsReel.log_message = lambda *a: None  # tests silencieux
        cls.serveur = ThreadingHTTPServer(
            ("127.0.0.1", 0), partial(fw.GestionnaireTempsReel, directory=str(fw.fausse_api.DOSSIER_FRONTEND)))
        cls.port = cls.serveur.server_address[1]
        threading.Thread(target=cls.serveur.serve_forever, daemon=True).start()
        cls.arret = fw.demarrer_diffusion(fw.GestionnaireTempsReel.parking, 0.2, journal=lambda *_: None)

    @classmethod
    def tearDownClass(cls):
        cls.arret.set()
        cls.serveur.shutdown()
        cls.serveur.server_close()

    def get_places(self, jeton=None):
        requete = urllib.request.Request(f"http://127.0.0.1:{self.port}/api/places")
        if jeton:
            requete.add_header("Authorization", f"Bearer {jeton}")
        try:
            with urllib.request.urlopen(requete, timeout=5) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_places_sans_jeton_401(self):
        statut, corps = self.get_places()
        self.assertEqual(statut, 401)
        self.assertEqual(corps["erreur"], "non_authentifie")

    def test_places_format_du_contrat(self):
        for jeton in ("conducteur", "exploitant"):
            statut, corps = self.get_places(jeton)
            self.assertEqual(statut, 200)
            self.assertEqual([p["id"] for p in corps["places"]], ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"])
            r = corps["resume"]
            self.assertEqual(r["total"], 8)
            self.assertEqual(r["libres"] + r["occupees"] + r["reservees"], 8)
            for p in corps["places"]:
                self.assertIn(p["etat"], fw.ETATS)
                self.assertTrue(p["depuis"].endswith("Z"))
            self.assertEqual(corps["places"][0]["type"], "pmr")

    def test_jeton_de_la_fausse_connexion_accepte(self):
        u = fw.fausse_api.UTILISATEURS["conducteur@exemple.tn"]
        self.assertEqual(self.get_places(fw.fausse_api.fabriquer_jeton(u))[0], 200)
        self.assertEqual(self.get_places("a.b.c")[0], 401)  # JWT illisible

    def test_websocket_sans_jeton_refuse(self):
        s, _, statut, _, _ = ouvrir_websocket(self.port, None)
        s.close()
        self.assertIn(" 401 ", statut)

    def test_websocket_recoit_les_changements(self):
        s, flux, statut, entetes, cle = ouvrir_websocket(self.port, "exploitant")
        try:
            self.assertEqual(statut, "HTTP/1.1 101 Switching Protocols")
            self.assertEqual(entetes["sec-websocket-accept"], fw.cle_acceptation(cle))
            opcode, donnees = fw.lire_trame(flux)
            self.assertEqual(opcode, 1)
            message = json.loads(donnees)
            self.assertEqual(set(message), {"place", "etat", "depuis"})
            self.assertIn(message["etat"], fw.ETATS)
            # Le changement diffusé est bien dans l'état complet (rechargé à la reconnexion)
            place = next(p for p in self.get_places("conducteur")[1]["places"] if p["id"] == message["place"])
            self.assertGreaterEqual(place["depuis"], message["depuis"])
            # Fermeture propre : le serveur confirme
            s.sendall(trame_client(8, b"\x03\xe8"))
            while (t := fw.lire_trame(flux)) and t[0] != 8:
                pass
            self.assertEqual(t[0], 8)
        finally:
            s.close()

    def test_cle_acceptation_rfc6455(self):
        # Exemple de la RFC 6455, §1.3
        self.assertEqual(fw.cle_acceptation("dGhlIHNhbXBsZSBub25jZQ=="), "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")


if __name__ == "__main__":
    unittest.main()
