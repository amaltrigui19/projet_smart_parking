#!/usr/bin/env python3
"""Relit les polygones enregistrés sur le serveur (GET /api/config/polygones) et affiche un résumé.

Sert à la démo du sprint 3 (« polygones enregistrés et relus par un script »). Marche avec la fausse API
(frontend/outils/fausse_api.py) comme avec le vrai serveur : il suffit de changer --url.

Utilisation (depuis la racine du dépôt) :
    python frontend/outils/relire_polygones.py                                   # fausse API, http://localhost:8000
    python frontend/outils/relire_polygones.py --url https://localhost --cacert server/certs/ca.pem
    python frontend/outils/relire_polygones.py --url https://smartparking-supcom.duckdns.org

Le jeton JWT (rôle EXPLOITANT) se donne avec --jeton, ou dans la variable d'environnement SMARTPARKING_JWT.
Avec la fausse API, le jeton « exploitant » suffit (valeur par défaut).

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
from valider_places import valider  # noqa: E402

ROUTE = "/api/config/polygones"
PLAN_IDS = ["A1", "A2", "A3", "A4", "B1", "B2", "B3", "B4"]  # contrat §4.3


def lire(url, jeton, cacert):
    req = urllib.request.Request(url.rstrip("/") + ROUTE, headers={
        "Accept": "application/json", "Authorization": f"Bearer {jeton}"})
    contexte = ssl.create_default_context(cafile=cacert) if cacert else None
    try:
        with urllib.request.urlopen(req, timeout=10, context=contexte) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return e.code, {}


def main():
    parser = argparse.ArgumentParser(description="Relit et résume les polygones enregistrés sur le serveur")
    parser.add_argument("--url", default="http://localhost:8000", help="adresse du serveur, sans /api")
    parser.add_argument("--jeton", default=os.environ.get("SMARTPARKING_JWT", "exploitant"),
                        help="JWT d'un exploitant (défaut : $SMARTPARKING_JWT, sinon « exploitant »)")
    parser.add_argument("--cacert", help="autorité de certification locale (HTTPS en local)")
    args = parser.parse_args()

    print(f"Relecture de {args.url.rstrip('/')}{ROUTE}")
    try:
        statut, d = lire(args.url, args.jeton, args.cacert)
    except (urllib.error.URLError, OSError) as e:
        print(f"[ERREUR] serveur injoignable : {getattr(e, 'reason', e)}")
        return 1
    if statut == 404:
        print("[INFO] aucun fichier de polygones enregistré sur le serveur.")
        return 1
    if statut != 200:
        print(f"[ERREUR] {statut} {d.get('erreur', '')} : {d.get('message', '')}")
        return 1

    places, zones = d.get("places", []), d.get("zones_interdites", [])
    largeur, hauteur = d.get("resolution", ["?", "?"])
    print(f"\nParking {d.get('parking')} · image {largeur} × {hauteur} px")
    print(f"{len(places)} place(s) · {len(zones)} zone(s) interdite(s)\n")

    print(f"{'Place':6s} {'Type':11s} {'Durée max':>10s} {'Points':>7s}")
    for p in sorted(places, key=lambda p: p.get("id", "")):
        print(f"{p.get('id', '?'):6s} {p.get('type', '?'):11s} {str(p.get('duree_max_min', '?')) + ' min':>10s} "
              f"{len(p.get('poly', [])):>7d}")
    types = Counter(p.get("type") for p in places)
    print("\nPar type : " + ", ".join(f"{t} {n}" for t, n in sorted(types.items())))
    print("Zones interdites : " + (", ".join(z.get("id", "?") for z in zones) or "aucune"))
    manquantes = [i for i in PLAN_IDS if i not in {p.get("id") for p in places}]
    print("Places du plan non dessinées : " + (", ".join(manquantes) or "aucune"))

    erreurs = valider(d)
    if erreurs:
        print(f"\n[ERREUR] {len(erreurs)} problème(s) par rapport au contrat :")
        for e in erreurs:
            print(f"  - {e}")
        return 1
    print("\n[OK] fichier relu et conforme au contrat des polygones (§5.2).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
