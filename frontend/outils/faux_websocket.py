#!/usr/bin/env python3
"""Faux serveur temps réel pour tester le plan de la PWA sans le vrai serveur (sprint 4).

Reprend TOUTE la fausse API (fausse_api.py : fichiers de la PWA, connexion, polygones) et ajoute,
selon le contrat (docs/contrats.md §1.4 et §2.6) :
    GET /api/places                   -> 200 + état complet des 8 places, 401 non_authentifie
    GET /api/ws/places?token=<jeton>  -> WebSocket : un changement aléatoire toutes les 2 s
                                          { "place": "A3", "etat": "occupee", "depuis": "...Z" }
Jetons acceptés : « exploitant », « conducteur », ou un jeton de la fausse connexion (connexion.html).
Sans jeton (ou jeton invalide ou expiré) : 401, et la poignée de main du WebSocket est refusée.

Bibliothèque standard uniquement : WebSocket minimal (RFC 6455 : messages texte, ping, fermeture).
Outil de test, jamais à déployer.

Lancer depuis la racine du dépôt :
    python frontend/outils/faux_websocket.py                    # http://localhost:8000
    python frontend/outils/faux_websocket.py --intervalle 1 --port 8001
Puis se connecter sur http://localhost:8000/connexion.html et ouvrir http://localhost:8000/plan-temps-reel.html
Couper (Ctrl+C) puis relancer le serveur : la page se reconnecte toute seule.
"""

import argparse
import base64
import hashlib
import json
import random
import struct
import sys
import threading
import time
from datetime import datetime, timezone
from functools import partial
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fausse_api  # noqa: E402

GUID_WEBSOCKET = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"  # RFC 6455, §1.3
ETATS = ["libre", "occupee", "reservee"]
# Plan du contrat §4.1 : (place, type, duree_max_min)
PLAN = [("A1", "pmr", 120), ("A2", "normale", 120), ("A3", "normale", 120), ("A4", "normale", 120),
        ("B1", "normale", 120), ("B2", "normale", 120), ("B3", "normale", 120), ("B4", "electrique", 60)]
JETONS_DE_TEST = {"exploitant", "conducteur"}


def maintenant():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def jeton_accepte(jeton):
    if not jeton:
        return False
    return jeton.lower() in JETONS_DE_TEST or fausse_api.lire_jeton(jeton) is not None


def cle_acceptation(cle):
    """Valeur de Sec-WebSocket-Accept pour la clé Sec-WebSocket-Key du navigateur."""
    return base64.b64encode(hashlib.sha1((cle + GUID_WEBSOCKET).encode("ascii")).digest()).decode("ascii")


def trame(opcode, donnees=b""):
    """Trame serveur -> client (jamais masquée). opcode 1 = texte, 8 = fermeture, 10 = pong."""
    n = len(donnees)
    if n < 126:
        entete = struct.pack("!BB", 0x80 | opcode, n)
    elif n < 1 << 16:
        entete = struct.pack("!BBH", 0x80 | opcode, 126, n)
    else:
        entete = struct.pack("!BBQ", 0x80 | opcode, 127, n)
    return entete + donnees


def lire_trame(flux):
    """Trame client -> serveur : (opcode, données démasquées), ou None si la connexion est fermée."""
    debut = flux.read(2)
    if len(debut) < 2:
        return None
    opcode, longueur = debut[0] & 0x0F, debut[1] & 0x7F
    masquee = debut[1] & 0x80
    if longueur == 126:
        longueur = struct.unpack("!H", flux.read(2))[0]
    elif longueur == 127:
        longueur = struct.unpack("!Q", flux.read(8))[0]
    if longueur > 1_000_000:
        raise ValueError("trame trop grande")
    masque = flux.read(4) if masquee else b""
    donnees = flux.read(longueur)
    if masquee:
        donnees = bytes(o ^ masque[i % 4] for i, o in enumerate(donnees))
    return opcode, donnees


class ClientWebSocket:
    def __init__(self, connexion):
        self.connexion = connexion
        self.verrou = threading.Lock()  # le fil de diffusion et le fil du client écrivent tous les deux

    def envoyer(self, octets):
        with self.verrou:
            self.connexion.sendall(octets)

    def envoyer_json(self, objet):
        self.envoyer(trame(1, json.dumps(objet, ensure_ascii=False).encode("utf-8")))


class Parking:
    """État des places (partagé par GET /api/places et le WebSocket) et clients connectés."""

    def __init__(self, graine=None):
        self.hasard = random.Random(graine)
        self.verrou = threading.Lock()
        self.clients = set()
        debut = maintenant()
        self.places = {pid: {"id": pid, "type": t, "etat": self.hasard.choice(["libre", "libre", "occupee"]),
                             "depuis": debut, "duree_max_min": d} for pid, t, d in PLAN}

    def etat_complet(self):
        with self.verrou:
            places = [dict(p) for p in self.places.values()]
        compte = {e: sum(p["etat"] == e for p in places) for e in ETATS}
        return {"parking": "P1", "ts": maintenant(),
                "resume": {"total": len(places), "libres": compte["libre"],
                           "occupees": compte["occupee"], "reservees": compte["reservee"]},
                "places": places}

    def changer_au_hasard(self):
        """Change l'état d'une place tirée au hasard ; renvoie le message du contrat §1.4."""
        with self.verrou:
            place = self.places[self.hasard.choice(list(self.places))]
            # Surtout libre <-> occupee, parfois reservee (comme le vrai parking)
            choix = [e for e in ["libre", "occupee", "libre", "occupee", "reservee"] if e != place["etat"]]
            place["etat"] = self.hasard.choice(choix)
            place["depuis"] = maintenant()
            return {"place": place["id"], "etat": place["etat"], "depuis": place["depuis"]}

    def diffuser(self, message):
        for client in list(self.clients):
            try:
                client.envoyer_json(message)
            except OSError:
                self.clients.discard(client)


def demarrer_diffusion(parking, intervalle, journal=print):
    """Fil qui change une place toutes les `intervalle` secondes et prévient les clients. Renvoie un Event d'arrêt."""
    arret = threading.Event()

    def boucle():
        while not arret.wait(intervalle):
            message = parking.changer_au_hasard()
            parking.diffuser(message)
            journal(f"{message['place']} -> {message['etat']} ({len(parking.clients)} client(s))")

    threading.Thread(target=boucle, daemon=True).start()
    return arret


class GestionnaireTempsReel(fausse_api.Gestionnaire):
    parking = None  # Parking partagé, fixé au démarrage

    def do_GET(self):
        if self.chemin() == "/api/places":
            return self.places()
        if self.chemin() == "/api/ws/places":
            return self.websocket()
        return super().do_GET()

    def places(self):
        entete = self.headers.get("Authorization", "")
        if not jeton_accepte(entete[7:].strip() if entete.startswith("Bearer ") else ""):
            return self.erreur(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.")
        self.envoyer_json(200, self.parking.etat_complet())

    def websocket(self):
        jeton = parse_qs(urlsplit(self.path).query).get("token", [""])[0]
        cle = self.headers.get("Sec-WebSocket-Key", "")
        if self.headers.get("Upgrade", "").lower() != "websocket" or not cle:
            return self.erreur(400, "requete_invalide", "Connexion WebSocket attendue.")
        if not jeton_accepte(jeton):
            return self.erreur(401, "non_authentifie", "JWT absent, invalide ou expiré : reconnectez-vous.")

        # Poignée de main (RFC 6455 §4.2.2), écrite à la main : elle doit être en HTTP/1.1.
        self.wfile.write((
            "HTTP/1.1 101 Switching Protocols\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Accept: {cle_acceptation(cle)}\r\n\r\n").encode("ascii"))
        self.log_request(101)
        self.close_connection = True

        client = ClientWebSocket(self.connection)
        self.parking.clients.add(client)
        try:
            while True:
                recue = lire_trame(self.rfile)
                if recue is None:
                    break
                opcode, donnees = recue
                if opcode == 8:  # fermeture demandée par le navigateur : on la confirme
                    client.envoyer(trame(8, donnees[:2]))
                    break
                if opcode == 9:  # ping -> pong
                    client.envoyer(trame(10, donnees))
        except (OSError, ValueError, struct.error):
            pass
        finally:
            self.parking.clients.discard(client)


def main():
    parser = argparse.ArgumentParser(description="Faux serveur temps réel (GET /api/places et WebSocket /api/ws/places)")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--intervalle", type=float, default=2.0, help="secondes entre deux changements")
    parser.add_argument("--graine", type=int, default=None, help="pour rejouer la même suite de changements")
    args = parser.parse_args()

    GestionnaireTempsReel.parking = Parking(args.graine)
    serveur = ThreadingHTTPServer(("127.0.0.1", args.port),
                                  partial(GestionnaireTempsReel, directory=str(fausse_api.DOSSIER_FRONTEND)))
    demarrer_diffusion(GestionnaireTempsReel.parking, args.intervalle)
    print(f"Faux serveur temps réel prêt : http://localhost:{args.port}/plan-temps-reel.html")
    print(f"Se connecter d'abord : http://localhost:{args.port}/connexion.html "
          "(exploitant@exemple.tn / exploitant123 ou conducteur@exemple.tn / conducteur123)")
    print(f"Un changement aléatoire toutes les {args.intervalle:g} s. Ctrl+C pour arrêter.")
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        print("\nArrêt.")
    finally:
        serveur.server_close()


if __name__ == "__main__":
    main()
