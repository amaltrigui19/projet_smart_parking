"""Tests of the fake API (fake_api.py): static files of the two applications, polygons (contract §2.10)
and authentication (§2.5).

Run from the root of the repository:
    python -m unittest frontend/tools/test_fake_api.py -v
"""

import json
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fake_api  # noqa: E402

VALID = {
    "parking": "P1",
    "resolution": [640, 480],
    "spots": [
        {"id": "A1", "type": "accessible", "max_duration_min": 120, "polygon": [[40, 60], [130, 60], [130, 200], [40, 200]]},
        {"id": "B4", "type": "electric", "max_duration_min": 60, "polygon": [[340, 280], [430, 280], [430, 420], [340, 420]]},
    ],
    "forbidden_zones": [{"id": "allee1", "polygon": [[500, 210], [639, 210], [639, 280], [500, 280]]}],
}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class TestFakeApi(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.TemporaryDirectory()
        fake_api.Handler.file = Path(cls.folder.name) / "polygons.json"
        fake_api.Handler.log_message = lambda *a: None  # quiet tests
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), fake_api.Handler)
        cls.base = f"http://127.0.0.1:{cls.server.server_address[1]}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.folder.cleanup()

    def call(self, method, path, body=None, token="operator", raw=None):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.build_opener(_NoRedirect).open(req) as r:
                return r.status, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    # ---------- Static files: same layout as Nginx ----------
    def test_0a_front_office_at_root(self):
        code, body = self.call("GET", "/login.html", token=None)
        self.assertEqual(code, 200)
        self.assertIn(b'data-app="front-office"', body)

    def test_0b_back_office_under_admin(self):
        code, body = self.call("GET", "/admin/", token=None)
        self.assertEqual(code, 200)
        self.assertIn("Connexion opérateur".encode("utf-8"), body)
        code, body = self.call("GET", "/admin/calibration.html", token=None)
        self.assertEqual(code, 200)
        self.assertIn(b'data-app="back-office"', body)

    def test_0c_shared_files(self):
        code, body = self.call("GET", "/shared/js/session.js", token=None)
        self.assertEqual(code, 200)
        self.assertIn(b"export async function login", body)

    def test_0d_admin_without_slash_redirects(self):
        code, _ = self.call("GET", "/admin", token=None)
        self.assertEqual(code, 301)

    def test_0e_back_office_not_served_at_root(self):
        code, _ = self.call("GET", "/calibration.html", token=None)
        self.assertEqual(code, 404)

    # ---------- Polygons ----------
    def test_1_nothing_saved_404(self):
        code, body = self.call("GET", "/api/config/polygons")
        self.assertEqual(code, 404)
        self.assertEqual(json.loads(body)["error"], "not_found")

    def test_2_no_token_401(self):
        code, body = self.call("PUT", "/api/config/polygons", VALID, token=None)
        self.assertEqual((code, json.loads(body)["error"]), (401, "unauthenticated"))

    def test_3_driver_403(self):
        code, body = self.call("PUT", "/api/config/polygons", VALID, token="driver")
        self.assertEqual((code, json.loads(body)["error"]), (403, "forbidden"))

    def test_4_invalid_config_400(self):
        invalid = json.loads(json.dumps(VALID))
        invalid["spots"][1]["id"] = "A1"            # duplicate id
        invalid["spots"][0]["max_duration_min"] = 0  # zero duration
        code, body = self.call("PUT", "/api/config/polygons", invalid)
        response = json.loads(body)
        self.assertEqual((code, response["error"]), (400, "invalid_config"))
        self.assertIn("en double", response["message"])
        self.assertIn("max_duration_min", response["message"])

    def test_5_spot_outside_plan_400(self):
        invalid = json.loads(json.dumps(VALID))
        invalid["spots"][0]["id"] = "P9"
        code, body = self.call("PUT", "/api/config/polygons", invalid)
        self.assertEqual(code, 400)
        self.assertIn("n'existe pas dans le plan", json.loads(body)["message"])

    def test_6_malformed_json_400(self):
        code, body = self.call("PUT", "/api/config/polygons", raw=b"{pas du json")
        self.assertEqual((code, json.loads(body)["error"]), (400, "invalid_request"))

    def test_7_save_then_read_back(self):
        code, body = self.call("PUT", "/api/config/polygons", VALID)
        self.assertEqual((code, json.loads(body)), (200, VALID))
        code, body = self.call("GET", "/api/config/polygons")
        self.assertEqual((code, json.loads(body)), (200, VALID))

    # ---------- Authentication (contract §2.5) ----------
    def login(self, email, password):
        code, body = self.call("POST", "/api/auth/login", {"email": email, "password": password}, token=None)
        return code, json.loads(body)

    def test_a_operator_login(self):
        code, r = self.login("operateur@exemple.tn", "operateur123")
        self.assertEqual(code, 200)
        self.assertEqual((r["type"], r["expires_in"], r["user"]["role"]), ("Bearer", 3600, "OPERATOR"))
        # The token opens /me and the operator routes.
        code, body = self.call("GET", "/api/auth/me", token=r["access_token"])
        self.assertEqual((code, json.loads(body)["email"]), (200, "operateur@exemple.tn"))
        code, _ = self.call("PUT", "/api/config/polygons", VALID, token=r["access_token"])
        self.assertEqual(code, 200)

    def test_b_driver_login_cannot_save_polygons(self):
        code, r = self.login("conducteur@exemple.tn", "conducteur123")
        self.assertEqual((code, r["user"]["role"]), (200, "DRIVER"))
        code, body = self.call("PUT", "/api/config/polygons", VALID, token=r["access_token"])
        self.assertEqual((code, json.loads(body)["error"]), (403, "forbidden"))

    def test_c_wrong_password_401(self):
        code, r = self.login("operateur@exemple.tn", "mauvais-mdp")
        self.assertEqual((code, r["error"]), (401, "invalid_credentials"))
        code2, r2 = self.login("inconnu@exemple.tn", "mauvais-mdp")
        self.assertEqual((code2, r2["message"]), (401, r["message"]))  # same message in both cases

    def test_d_register_then_login(self):
        body = {"name": "Sara Ben Ali", "email": "Sara@Exemple.tn", "password": "motdepasse8", "role": "OPERATOR"}
        code, r = self.call("POST", "/api/auth/register", body, token=None)
        r = json.loads(r)
        self.assertEqual((code, r["role"], r["email"]), (201, "DRIVER", "sara@exemple.tn"))  # role ignored
        code, r = self.call("POST", "/api/auth/register", body, token=None)
        self.assertEqual((code, json.loads(r)["error"]), (409, "email_already_exists"))
        code, r = self.login("sara@exemple.tn", "motdepasse8")
        self.assertEqual((code, r["user"]["name"]), (200, "Sara Ben Ali"))

    def test_e_invalid_registration_400(self):
        code, body = self.call("POST", "/api/auth/register",
                               {"name": "X", "email": "x@exemple.tn", "password": "court"}, token=None)
        self.assertEqual((code, json.loads(body)["error"]), (400, "invalid_request"))

    def test_f_me_without_token_or_bad_token_401(self):
        for token in (None, "bidon", "a.b.c"):
            code, body = self.call("GET", "/api/auth/me", token=token)
            self.assertEqual((code, json.loads(body)["error"]), (401, "unauthenticated"))


if __name__ == "__main__":
    unittest.main()
