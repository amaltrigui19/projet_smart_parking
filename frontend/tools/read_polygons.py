#!/usr/bin/env python3
"""Relit les polygones enregistrés sur le serveur (GET /api/config/polygons) et affiche un résumé.

Marche avec la fausse API (frontend/tools/fake_api.py) comme avec le vrai serveur : il suffit de changer --url.

Utilisation (depuis la racine du dépôt) :
    python frontend/tools/read_polygons.py                                   # fausse API, http://localhost:8000
    python frontend/tools/read_polygons.py --url https://localhost --cacert server/certs/ca.pem
    python frontend/tools/read_polygons.py --url https://smartparking-supcom.duckdns.org

Le jeton JWT (rôle OPERATOR) se donne avec --token, ou dans la variable d'environnement SMARTPARKING_JWT.
Avec la fausse API, le jeton « operator » suffit (valeur par défaut).

Code de sortie : 0 si le fichier est relu et conforme au contrat, 1 sinon.
"""

import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_spots import validate  # noqa: E402

ROUTE = "/api/config/polygons"
PLAN_IDS = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]  # contract §4.3


def fetch(url, token, cacert):
    req = urllib.request.Request(url.rstrip("/") + ROUTE, headers={
        "Accept": "application/json", "Authorization": f"Bearer {token}"})
    context = ssl.create_default_context(cafile=cacert) if cacert else None
    try:
        with urllib.request.urlopen(req, timeout=10, context=context) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return e.code, {}


def main():
    parser = argparse.ArgumentParser(description="Relit et résume les polygones enregistrés sur le serveur")
    parser.add_argument("--url", default="http://localhost:8000", help="adresse du serveur, sans /api")
    parser.add_argument("--token", default=os.environ.get("SMARTPARKING_JWT", "operator"),
                        help="JWT d'un opérateur (défaut : $SMARTPARKING_JWT, sinon « operator »)")
    parser.add_argument("--cacert", help="autorité de certification locale (HTTPS en local)")
    args = parser.parse_args()

    print(f"Relecture de {args.url.rstrip('/')}{ROUTE}")
    try:
        status, d = fetch(args.url, args.token, args.cacert)
    except (urllib.error.URLError, OSError) as e:
        print(f"[ERREUR] serveur injoignable : {getattr(e, 'reason', e)}")
        return 1
    if status == 404:
        print("[INFO] aucun fichier de polygones enregistré sur le serveur.")
        return 1
    if status != 200:
        print(f"[ERREUR] {status} {d.get('error', '')} : {d.get('message', '')}")
        return 1

    spots, zones = d.get("spots", []), d.get("forbidden_zones", [])
    width, height = d.get("resolution", ["?", "?"])
    print(f"\nParking {d.get('parking')} · image {width} × {height} px")
    print(f"{len(spots)} place(s) · {len(zones)} zone(s) interdite(s)\n")

    print(f"{'Place':6s} {'Type':11s} {'Durée max':>10s} {'Points':>7s}")
    for s in sorted(spots, key=lambda s: s.get("id", "")):
        print(f"{s.get('id', '?'):6s} {s.get('type', '?'):11s} {str(s.get('max_duration_min', '?')) + ' min':>10s} "
              f"{len(s.get('polygon', [])):>7d}")
    types = Counter(s.get("type") for s in spots)
    print("\nPar type : " + ", ".join(f"{t} {n}" for t, n in sorted(types.items())))
    print("Zones interdites : " + (", ".join(z.get("id", "?") for z in zones) or "aucune"))
    missing = [i for i in PLAN_IDS if i not in {s.get("id") for s in spots}]
    print("Places du plan non dessinées : " + (", ".join(missing) or "aucune"))

    errors = validate(d)
    if errors:
        print(f"\n[ERREUR] {len(errors)} problème(s) par rapport au contrat :")
        for e in errors:
            print(f"  - {e}")
        return 1
    print("\n[OK] fichier relu et conforme au contrat des polygones (§5.2).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
