"""Tests of the fake real-time server (fake_realtime.py): GET /api/spots (§2.6) and WebSocket (§1.4).

Run from the root of the repository:
    python -m unittest frontend/tools/test_fake_realtime.py -v
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
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fake_realtime as fr  # noqa: E402

SPOT_IDS = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]


def open_websocket(port, token):
    """Handshake by hand. Returns (socket, stream, status line, headers, key sent)."""
    key = base64.b64encode(os.urandom(16)).decode()
    s = socket.create_connection(("127.0.0.1", port), timeout=5)
    path = "/api/ws/spots" + (f"?token={token}" if token else "")
    s.sendall((f"GET {path} HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
               f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    stream = s.makefile("rb")
    status = stream.readline().decode().strip()
    headers = {}
    while (line := stream.readline().decode().strip()):
        name, _, value = line.partition(":")
        headers[name.strip().lower()] = value.strip()
    return s, stream, status, headers, key


def client_frame(opcode, data=b""):
    """Client -> server frame, masked as required by RFC 6455."""
    mask = os.urandom(4)
    return bytes([0x80 | opcode, 0x80 | len(data)]) + mask + bytes(o ^ mask[i % 4] for i, o in enumerate(data))


class TestFakeRealtime(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        fr.RealtimeHandler.parking = fr.Parking(seed=1)
        fr.RealtimeHandler.log_message = lambda *a: None  # quiet tests
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), fr.RealtimeHandler)
        cls.port = cls.server.server_address[1]
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.stop = fr.start_broadcast(fr.RealtimeHandler.parking, 0.2, log=lambda *_: None)

    @classmethod
    def tearDownClass(cls):
        cls.stop.set()
        cls.server.shutdown()
        cls.server.server_close()

    def get_spots(self, token=None):
        request = urllib.request.Request(f"http://127.0.0.1:{self.port}/api/spots")
        if token:
            request.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(request, timeout=5) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_spots_without_token_401(self):
        status, body = self.get_spots()
        self.assertEqual(status, 401)
        self.assertEqual(body["error"], "unauthenticated")

    def test_spots_contract_format(self):
        for token in ("driver", "operator"):
            status, body = self.get_spots(token)
            self.assertEqual(status, 200)
            self.assertEqual([s["id"] for s in body["spots"]], SPOT_IDS)
            summary = body["summary"]
            self.assertEqual(summary["total"], 8)
            self.assertEqual(summary["free"] + summary["occupied"] + summary["reserved"], 8)
            for s in body["spots"]:
                self.assertIn(s["state"], fr.STATES)
                self.assertTrue(s["since"].endswith("Z"))
                self.assertEqual(set(s), {"id", "type", "state", "since", "max_duration_min"})
            self.assertEqual(body["spots"][0]["type"], "accessible")

    def test_token_of_the_fake_login_accepted(self):
        u = fr.fake_api.USERS["conducteur@exemple.tn"]
        self.assertEqual(self.get_spots(fr.fake_api.make_token(u))[0], 200)
        self.assertEqual(self.get_spots("a.b.c")[0], 401)  # unreadable JWT

    def test_websocket_without_token_refused(self):
        s, _, status, _, _ = open_websocket(self.port, None)
        s.close()
        self.assertIn(" 401 ", status)

    def test_websocket_receives_changes(self):
        s, stream, status, headers, key = open_websocket(self.port, "operator")
        try:
            self.assertEqual(status, "HTTP/1.1 101 Switching Protocols")
            self.assertEqual(headers["sec-websocket-accept"], fr.accept_key(key))
            opcode, data = fr.read_frame(stream)
            self.assertEqual(opcode, 1)
            message = json.loads(data)
            self.assertEqual(set(message), {"spot", "state", "since"})
            self.assertIn(message["state"], fr.STATES)
            # The broadcast change is in the full state (reloaded on reconnection)
            spot = next(s for s in self.get_spots("driver")[1]["spots"] if s["id"] == message["spot"])
            self.assertGreaterEqual(spot["since"], message["since"])
            # Clean close: the server confirms
            s.sendall(client_frame(8, b"\x03\xe8"))
            while (f := fr.read_frame(stream)) and f[0] != 8:
                pass
            self.assertEqual(f[0], 8)
        finally:
            s.close()

    def test_accept_key_rfc6455(self):
        # Example of RFC 6455, §1.3
        self.assertEqual(fr.accept_key("dGhlIHNhbXBsZSBub25jZQ=="), "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")


if __name__ == "__main__":
    unittest.main()
