#!/usr/bin/env python3
"""Vérifie qu'un places.json respecte le contrat des polygones (docs/contrats.md §5.2).

Mêmes règles que le serveur (middleware/core CalibrationValidator) et que back-office/js/spot-validation.js.

Usage : python frontend/tools/validate_spots.py chemin/vers/places.json
Code de sortie : 0 si le fichier est valide, 1 sinon.
"""
import json
import re
import sys

TYPES = {"standard", "accessible", "electric"}
VALID_ID = re.compile(r"^[A-Za-z0-9_-]+$")


def is_integer(v):
    return isinstance(v, int) and not isinstance(v, bool)


def area(polygon):
    """Aire du polygone (formule du lacet)."""
    s = 0
    for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
        s += x1 * y2 - x2 * y1
    return abs(s) / 2


def check_polygon(label, polygon, width, height, errors):
    if not isinstance(polygon, list) or len(polygon) < 3:
        errors.append(f"{label} : 'polygon' doit être une liste d'au moins 3 points")
        return
    for i, p in enumerate(polygon):
        if not (isinstance(p, list) and len(p) == 2 and all(is_integer(c) for c in p)):
            errors.append(f"{label} : le point {i} doit être [x, y] en pixels entiers")
            return
        x, y = p
        if not (0 <= x < width and 0 <= y < height):
            errors.append(f"{label} : le point {i} {p} sort de l'image {width} × {height}")
    if area(polygon) == 0:
        errors.append(f"{label} : le polygone est plat (aire nulle)")


def validate(d):
    errors = []
    if not isinstance(d, dict):
        return ["le fichier doit contenir un objet JSON"]

    if not isinstance(d.get("parking"), str) or not d["parking"]:
        errors.append("'parking' doit être un texte non vide, par exemple \"P1\"")

    res = d.get("resolution")
    if not (isinstance(res, list) and len(res) == 2 and all(is_integer(v) and v > 0 for v in res)):
        errors.append("'resolution' doit être [largeur, hauteur] en pixels")
        width = height = 10**9  # on continue la vérification sans limite
    else:
        width, height = res

    ids = set()

    def check_id(label, element):
        id_ = element.get("id")
        if not isinstance(id_, str) or not VALID_ID.match(id_):
            errors.append(f"{label} : 'id' manquant ou invalide")
            return label
        if id_ in ids:
            errors.append(f"{id_} : identifiant en double")
        ids.add(id_)
        return id_

    spots = d.get("spots")
    if not isinstance(spots, list):
        errors.append("'spots' doit être une liste")
        spots = []
    for i, s in enumerate(spots):
        if not isinstance(s, dict):
            errors.append(f"spots[{i}] doit être un objet")
            continue
        label = check_id(f"spots[{i}]", s)
        if s.get("type") not in TYPES:
            errors.append(f"{label} : 'type' doit être l'un de {sorted(TYPES)}")
        duration = s.get("max_duration_min")
        if not (is_integer(duration) and duration >= 1):
            errors.append(f"{label} : 'max_duration_min' doit être un entier ≥ 1")
        check_polygon(label, s.get("polygon"), width, height, errors)

    zones = d.get("forbidden_zones")
    if not isinstance(zones, list):
        errors.append("'forbidden_zones' doit être une liste")
        zones = []
    for i, z in enumerate(zones):
        if not isinstance(z, dict):
            errors.append(f"forbidden_zones[{i}] doit être un objet")
            continue
        check_polygon(check_id(f"forbidden_zones[{i}]", z), z.get("polygon"), width, height, errors)

    return errors


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    try:
        with open(sys.argv[1], encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"[ERREUR] lecture impossible : {e}")
        return 1

    errors = validate(data)
    if errors:
        for e in errors:
            print(f"[ERREUR] {e}")
        print(f"{len(errors)} erreur(s) : fichier non conforme au contrat.")
        return 1

    w, h = data["resolution"]
    print(f"[OK] {len(data['spots'])} place(s), {len(data['forbidden_zones'])} "
          f"zone(s) interdite(s), résolution {w} × {h}. Fichier conforme au contrat.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
