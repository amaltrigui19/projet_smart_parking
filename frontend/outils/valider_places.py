#!/usr/bin/env python3
"""Vérifie qu'un places.json respecte le contrat des polygones (docs/contrats.md §5.2).

Usage : python frontend/outils/valider_places.py chemin/vers/places.json
Code de sortie : 0 si le fichier est valide, 1 sinon.
"""
import json
import re
import sys

TYPES = {"normale", "pmr", "electrique"}
ID_VALIDE = re.compile(r"^[A-Za-z0-9_-]+$")


def est_entier(v):
    return isinstance(v, int) and not isinstance(v, bool)


def aire(poly):
    """Aire du polygone (formule du lacet)."""
    s = 0
    for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
        s += x1 * y2 - x2 * y1
    return abs(s) / 2


def verifier_poly(nom, poly, largeur, hauteur, erreurs):
    if not isinstance(poly, list) or len(poly) < 3:
        erreurs.append(f"{nom} : 'poly' doit être une liste d'au moins 3 points")
        return
    for i, p in enumerate(poly):
        if not (isinstance(p, list) and len(p) == 2 and all(est_entier(c) for c in p)):
            erreurs.append(f"{nom} : le point {i} doit être [x, y] en pixels entiers")
            return
        x, y = p
        if not (0 <= x < largeur and 0 <= y < hauteur):
            erreurs.append(f"{nom} : le point {i} {p} sort de l'image {largeur} × {hauteur}")
    if aire(poly) == 0:
        erreurs.append(f"{nom} : le polygone est plat (aire nulle)")


def valider(d):
    erreurs = []
    if not isinstance(d, dict):
        return ["le fichier doit contenir un objet JSON"]

    if not isinstance(d.get("parking"), str) or not d["parking"]:
        erreurs.append("'parking' doit être un texte non vide, par exemple \"P1\"")

    res = d.get("resolution")
    if not (isinstance(res, list) and len(res) == 2 and all(est_entier(v) and v > 0 for v in res)):
        erreurs.append("'resolution' doit être [largeur, hauteur] en pixels")
        largeur = hauteur = 10**9  # on continue la vérification sans limite
    else:
        largeur, hauteur = res

    ids = set()

    def verifier_id(nom, element):
        id_ = element.get("id")
        if not isinstance(id_, str) or not ID_VALIDE.match(id_):
            erreurs.append(f"{nom} : 'id' manquant ou invalide")
            return nom
        if id_ in ids:
            erreurs.append(f"{id_} : identifiant en double")
        ids.add(id_)
        return id_

    places = d.get("places")
    if not isinstance(places, list):
        erreurs.append("'places' doit être une liste")
        places = []
    for i, p in enumerate(places):
        if not isinstance(p, dict):
            erreurs.append(f"places[{i}] doit être un objet")
            continue
        nom = verifier_id(f"places[{i}]", p)
        if p.get("type") not in TYPES:
            erreurs.append(f"{nom} : 'type' doit être l'un de {sorted(TYPES)}")
        duree = p.get("duree_max_min")
        if not (est_entier(duree) and duree >= 1):
            erreurs.append(f"{nom} : 'duree_max_min' doit être un entier ≥ 1")
        verifier_poly(nom, p.get("poly"), largeur, hauteur, erreurs)

    zones = d.get("zones_interdites")
    if not isinstance(zones, list):
        erreurs.append("'zones_interdites' doit être une liste")
        zones = []
    for i, z in enumerate(zones):
        if not isinstance(z, dict):
            erreurs.append(f"zones_interdites[{i}] doit être un objet")
            continue
        nom = verifier_id(f"zones_interdites[{i}]", z)
        verifier_poly(nom, z.get("poly"), largeur, hauteur, erreurs)

    return erreurs


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    try:
        with open(sys.argv[1], encoding="utf-8") as f:
            donnees = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"[ERREUR] lecture impossible : {e}")
        return 1

    erreurs = valider(donnees)
    if erreurs:
        for e in erreurs:
            print(f"[ERREUR] {e}")
        print(f"{len(erreurs)} erreur(s) : fichier non conforme au contrat.")
        return 1

    l, h = donnees["resolution"]
    print(f"[OK] {len(donnees['places'])} place(s), {len(donnees['zones_interdites'])} "
          f"zone(s) interdite(s), résolution {l} × {h}. Fichier conforme au contrat.")
    return 0


if __name__ == "__main__":
    sys.exit(main())