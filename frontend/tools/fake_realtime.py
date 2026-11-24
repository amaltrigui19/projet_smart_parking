#!/usr/bin/env python3
"""Faux serveur temps réel pour tester le plan des deux frontends sans le vrai serveur.

Reprend TOUTE la fausse API (fake_api.py : fichiers des deux applications, connexion, polygones) et ajoute,
selon le contrat (docs/contrats.md §1.4 et §2.6) :
    GET /api/spots                   -> 200 + état complet des 8 places, 401 unauthenticated
    GET /api/ws/spots?token=<jeton>  -> WebSocket : un changement aléatoire toutes les 2 s
                                         { "spot": "A3", "state": "occupied", "since": "...Z" }
Jetons acceptés : « operator », « driver », ou un jeton de la fausse connexion.
Sans jeton (ou jeton invalide ou expiré) : 401, et la poignée de main du WebSocket est refusée.

Bibliothèque standard uniquement : WebSocket minimal (RFC 6455 : messages texte, ping, fermeture).
Outil de test, jamais à déployer.

Lancer depuis la racine du dépôt :
    python frontend/tools/fake_realtime.py                    # http://localhost:8000
    python frontend/tools/fake_realtime.py --interval 1 --port 8001
Puis se connecter sur http://localhost:8000/login.html et ouvrir http://localhost:8000/live-map.html
(ou http://localhost:8000/admin/ puis le plan en temps réel du back office).
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
from datetime import datetime, timezone
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fake_api  # noqa: E402

WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"  # RFC 6455, §1.3
STATES = ["free", "occupied", "reserved"]
# Plan of contract §4.1: (spot, type, max_duration_min)
PLAN = [("A1", "accessible", 120), ("A2", "standard", 120), ("A3", "standard", 120), ("A4", "standard", 120),
        ("B1", "standard", 120), ("B2", "standard", 120), ("B3", "standard", 120), ("B4", "electric", 60)]


def now():
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def token_accepted(token):
    return fake_api.token_role(token) is not None


def accept_key(key):
    """Value of Sec-WebSocket-Accept for the browser's Sec-WebSocket-Key."""
    return base64.b64encode(hashlib.sha1((key + WEBSOCKET_GUID).encode("ascii")).digest()).decode("ascii")


def frame(opcode, data=b""):
    """Server -> client frame (never masked). opcode 1 = text, 8 = close, 10 = pong."""
    n = len(data)
    if n < 126:
        header = struct.pack("!BB", 0x80 | opcode, n)
    elif n < 1 << 16:
        header = struct.pack("!BBH", 0x80 | opcode, 126, n)
    else:
        header = struct.pack("!BBQ", 0x80 | opcode, 127, n)
    return header + data


def read_frame(stream):
    """Client -> server frame: (opcode, unmasked data), or None if the connection is closed."""
    start = stream.read(2)
    if len(start) < 2:
        return None
    opcode, length = start[0] & 0x0F, start[1] & 0x7F
    masked = start[1] & 0x80
    if length == 126:
        length = struct.unpack("!H", stream.read(2))[0]
    elif length == 127:
        length = struct.unpack("!Q", stream.read(8))[0]
    if length > 1_000_000:
        raise ValueError("frame too large")
    mask = stream.read(4) if masked else b""
    data = stream.read(length)
    if masked:
        data = bytes(o ^ mask[i % 4] for i, o in enumerate(data))
    return opcode, data


class WebSocketClient:
    def __init__(self, connection):
        self.connection = connection
        self.lock = threading.Lock()  # the broadcast thread and the client thread both write

    def send(self, data):
        with self.lock:
            self.connection.sendall(data)

    def send_json(self, obj):
        self.send(frame(1, json.dumps(obj, ensure_ascii=False).encode("utf-8")))


class Parking:
    """State of the spots (shared by GET /api/spots and the WebSocket) and connected clients."""

    def __init__(self, seed=None):
        self.random = random.Random(seed)
        self.lock = threading.Lock()
        self.clients = set()
        start = now()
        self.spots = {sid: {"id": sid, "type": t, "state": self.random.choice(["free", "free", "occupied"]),
                            "since": start, "max_duration_min": d} for sid, t, d in PLAN}

    def full_state(self):
        with self.lock:
            spots = [dict(s) for s in self.spots.values()]
        count = {st: sum(s["state"] == st for s in spots) for st in STATES}
        return {"parking": "P1", "ts": now(),
                "summary": {"total": len(spots), **count},
                "spots": spots}

    def random_change(self):
        """Changes the state of a random spot; returns the message of contract §1.4."""
        with self.lock:
            spot = self.spots[self.random.choice(list(self.spots))]
            # Mostly free <-> occupied, sometimes reserved (like the real parking)
            choices = [st for st in ["free", "occupied", "free", "occupied", "reserved"] if st != spot["state"]]
            spot["state"] = self.random.choice(choices)
            spot["since"] = now()
            return {"spot": spot["id"], "state": spot["state"], "since": spot["since"]}

    def broadcast(self, message):
        for client in list(self.clients):
            try:
                client.send_json(message)
            except OSError:
                self.clients.discard(client)


def start_broadcast(parking, interval, log=print):
    """Thread that changes a spot every `interval` seconds and tells the clients. Returns a stop Event."""
    stop = threading.Event()

    def loop():
        while not stop.wait(interval):
            message = parking.random_change()
            parking.broadcast(message)
            log(f"{message['spot']} -> {message['state']} ({len(parking.clients)} client(s))")

    threading.Thread(target=loop, daemon=True).start()
    return stop


class RealtimeHandler(fake_api.Handler):
    parking = None  # shared Parking, set at startup

    def do_GET(self):
        if self.route() == "/api/spots":
            return self.spots()
        if self.route() == "/api/ws/spots":
            return self.websocket()
        return super().do_GET()

    def spots(self):
        if not token_accepted(self.bearer()):
            return self.error(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.")
        self.send_json(200, self.parking.full_state())

    def websocket(self):
        token = parse_qs(urlsplit(self.path).query).get("token", [""])[0]
        key = self.headers.get("Sec-WebSocket-Key", "")
        if self.headers.get("Upgrade", "").lower() != "websocket" or not key:
            return self.error(400, "invalid_request", "Connexion WebSocket attendue.")
        if not token_accepted(token):
            return self.error(401, "unauthenticated", "JWT absent, invalide ou expiré : reconnectez-vous.")

        # Handshake (RFC 6455 §4.2.2), written by hand: it must be HTTP/1.1.
        self.wfile.write((
            "HTTP/1.1 101 Switching Protocols\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Accept: {accept_key(key)}\r\n\r\n").encode("ascii"))
        self.log_request(101)
        self.close_connection = True

        client = WebSocketClient(self.connection)
        self.parking.clients.add(client)
        try:
            while True:
                received = read_frame(self.rfile)
                if received is None:
                    break
                opcode, data = received
                if opcode == 8:  # close requested by the browser: confirm it
                    client.send(frame(8, data[:2]))
                    break
                if opcode == 9:  # ping -> pong
                    client.send(frame(10, data))
        except (OSError, ValueError, struct.error):
            pass
        finally:
            self.parking.clients.discard(client)


def main():
    parser = argparse.ArgumentParser(description="Faux serveur temps réel (GET /api/spots et WebSocket /api/ws/spots)")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--interval", type=float, default=2.0, help="secondes entre deux changements")
    parser.add_argument("--seed", type=int, default=None, help="pour rejouer la même suite de changements")
    args = parser.parse_args()

    RealtimeHandler.parking = Parking(args.seed)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), RealtimeHandler)
    start_broadcast(RealtimeHandler.parking, args.interval)
    print(f"Faux serveur temps réel prêt : http://localhost:{args.port}/live-map.html "
          f"et http://localhost:{args.port}/admin/live-map.html")
    print("Se connecter d'abord : conducteur@exemple.tn / conducteur123 (/login.html) "
          "ou operateur@exemple.tn / operateur123 (/admin/)")
    print(f"Un changement aléatoire toutes les {args.interval:g} s. Ctrl+C pour arrêter.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nArrêt.")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
