"""
Sprint 3 — Générer le lot 3 synthétique : la maquette vue du dessus par la caméra fixe.

En attendant la vraie maquette, le script dessine des images 640 x 480 qui reprennent sa géométrie
(contrat §4.3 : 60 x 40 cm, places A1-A4 et B1-B4 de 8 x 13 cm, allée centrale, zone interdite allee1)
et y place des voitures miniatures vues du dessus. Les boîtes YOLO sont calculées en même temps que
le dessin : elles sont exactes, il n'y a rien à annoter à la main.

Ce qui varie d'une image à l'autre (pour que YOLO généralise) :
    - position, hauteur et légère rotation de la caméra ;
    - couleur et texture du sol, couleur des lignes ;
    - nombre, couleur, taille et orientation des voitures ;
    - cas difficiles : parking vide, voiture à cheval sur deux places, voiture dans la zone interdite ;
    - lumière (gradient, ombres), flou, bruit, compression JPEG.

Sortie : <sortie>/images/synth_lot3_00001.jpg + <sortie>/labels/synth_lot3_00001.txt (format YOLO,
classe 0 = voiture) et <sortie>/scenarios.csv (contenu de chaque image, utile pour tester les anomalies).

Utilisation (depuis la racine du dépôt) :
    python iot/annotation/generer_synthetique.py --nombre 300
    python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot3 --apercu 12
"""

import argparse
import csv
import math
import random
import shutil
from pathlib import Path

import cv2
import numpy as np

LARGEUR, HAUTEUR = 640, 480                       # résolution de la caméra (contrat 5.2)
MAQUETTE_CM = (60, 40)                             # dimensions de la maquette (contrat 4.3)
PLACES_CM = {                                      # rect_cm du contrat 4.3 : x, y, largeur, hauteur
    "A1": (14, 0, 8, 13), "A2": (22, 0, 8, 13), "A3": (30, 0, 8, 13), "A4": (38, 0, 8, 13),
    "B1": (14, 27, 8, 13), "B2": (22, 27, 8, 13), "B3": (30, 27, 8, 13), "B4": (38, 27, 8, 13),
}
ZONE_INTERDITE_CM = (50, 13, 10, 14)               # allee1
SORTIE_PAR_DEFAUT = Path(__file__).resolve().parent.parent / "dataset" / "lot3"
PREFIXE = "synth_lot3_"
# Couleurs de voitures (BGR), sans noir : le protocole de la maquette exclut les voitures noires
COULEURS = [(40, 40, 200), (200, 80, 30), (30, 160, 230), (60, 170, 60), (220, 220, 220),
            (160, 160, 160), (30, 200, 200), (140, 50, 140), (90, 120, 200), (200, 170, 120)]


# ---------- Géométrie : de la maquette (cm) à l'image (pixels) ----------

def camera_aleatoire(rng):
    """Matrice 2 x 3 cm -> pixels : la maquette remplit 80 à 98 % de l'image, tournée de ± 4°."""
    echelle = rng.uniform(0.80, 0.98) * min(LARGEUR / MAQUETTE_CM[0], HAUTEUR / MAQUETTE_CM[1])
    angle = math.radians(rng.uniform(-4, 4))
    c, s = math.cos(angle) * echelle, math.sin(angle) * echelle
    cx_cm, cy_cm = MAQUETTE_CM[0] / 2, MAQUETTE_CM[1] / 2
    tx = LARGEUR / 2 + rng.uniform(-15, 15) - (c * cx_cm - s * cy_cm)
    ty = HAUTEUR / 2 + rng.uniform(-12, 12) - (s * cx_cm + c * cy_cm)
    return np.array([[c, -s, tx], [s, c, ty]]), echelle


def vers_pixels(matrice, points_cm):
    pts = np.hstack([np.asarray(points_cm, dtype=np.float64), np.ones((len(points_cm), 1))])
    return pts @ matrice.T


def coins_rect(x, y, l, h):
    return [(x, y), (x + l, y), (x + l, y + h), (x, y + h)]


# ---------- Dessin du parking ----------

def dessiner_sol(rng):
    base = rng.randint(70, 150)
    teinte = np.array([base + rng.randint(-8, 8) for _ in range(3)], dtype=np.float32)
    img = np.ones((HAUTEUR, LARGEUR, 3), np.float32) * teinte
    grain = rng.uniform(4, 14)
    img += np.random.default_rng(rng.randint(0, 10**9)).normal(0, grain, (HAUTEUR, LARGEUR, 1))
    return cv2.GaussianBlur(img, (0, 0), rng.uniform(0.5, 1.5))


def dessiner_parking(img, matrice, echelle, rng):
    couleur_ligne = rng.choice([(235, 235, 235), (60, 210, 230), (245, 245, 225)])
    epaisseur = max(1, round(echelle * rng.uniform(0.25, 0.45)))
    for pid, rect in PLACES_CM.items():
        pts = vers_pixels(matrice, coins_rect(*rect)).astype(np.int32)
        cv2.polylines(img, [pts], True, couleur_ligne, epaisseur, cv2.LINE_AA)
        if pid == "A1":                                   # place PMR : marquage bleu au sol
            x, y, l, h = rect
            marque = vers_pixels(matrice, coins_rect(x + 2, y + 4, l - 4, h - 8)).astype(np.int32)
            cv2.fillPoly(img, [marque], (170, 90, 30), cv2.LINE_AA)
        if pid == "B4":                                   # place électrique : marquage vert
            x, y, l, h = rect
            marque = vers_pixels(matrice, coins_rect(x + 2, y + 4, l - 4, h - 8)).astype(np.int32)
            cv2.fillPoly(img, [marque], (60, 150, 60), cv2.LINE_AA)
    # Zone interdite : contour et hachures
    x, y, l, h = ZONE_INTERDITE_CM
    cv2.polylines(img, [vers_pixels(matrice, coins_rect(x, y, l, h)).astype(np.int32)], True,
                  (40, 200, 230), epaisseur, cv2.LINE_AA)
    for d in np.arange(0, l + h, 2.5):
        p1 = (x + max(0, d - h), y + min(h, d))
        p2 = (x + min(l, d), y + max(0, d - l))
        a, b = vers_pixels(matrice, [p1, p2]).astype(np.int32)
        cv2.line(img, tuple(int(v) for v in a), tuple(int(v) for v in b), (40, 200, 230), epaisseur, cv2.LINE_AA)


# ---------- Voitures ----------

def dessiner_voiture(rng, echelle):
    """Voiture vue du dessus sur un calque transparent. Renvoie (calque BGR, masque 0-255)."""
    longueur = int(rng.uniform(7.0, 10.0) * echelle)        # miniatures d'environ 7 à 10 cm
    largeur = int(longueur * rng.uniform(0.42, 0.52))
    marge = int(longueur * 0.15)
    H, W = longueur + 2 * marge, largeur + 2 * marge
    calque = np.zeros((H, W, 3), np.uint8)
    masque = np.zeros((H, W), np.uint8)
    x0, y0, x1, y1 = marge, marge, marge + largeur, marge + longueur
    rayon = max(2, largeur // 4)
    couleur = tuple(int(np.clip(c + rng.randint(-25, 25), 0, 255)) for c in rng.choice(COULEURS))
    # Roues (dépassent un peu de la carrosserie)
    roue_l, roue_h = max(2, largeur // 7), max(3, longueur // 7)
    for yr in (y0 + longueur // 6, y1 - longueur // 6 - roue_h):
        for xr in (x0 - roue_l // 2, x1 - roue_l // 2):
            cv2.rectangle(calque, (xr, yr), (xr + roue_l, yr + roue_h), (25, 25, 25), -1)
            cv2.rectangle(masque, (xr, yr), (xr + roue_l, yr + roue_h), 255, -1)
    # Carrosserie : rectangle aux coins arrondis
    for m, val in ((calque, couleur), (masque, 255)):
        cv2.rectangle(m, (x0 + rayon, y0), (x1 - rayon, y1), val, -1)
        cv2.rectangle(m, (x0, y0 + rayon), (x1, y1 - rayon), val, -1)
        for cx, cy in ((x0 + rayon, y0 + rayon), (x1 - rayon, y0 + rayon), (x0 + rayon, y1 - rayon), (x1 - rayon, y1 - rayon)):
            cv2.circle(m, (cx, cy), rayon, val, -1, cv2.LINE_AA)
    # Pare-brise, lunette arrière et toit
    vitre = (55, 45, 40)
    l3 = largeur // 6
    pare_brise = np.array([[x0 + l3, y0 + longueur * 0.28], [x1 - l3, y0 + longueur * 0.28],
                           [x1 - l3 // 2, y0 + longueur * 0.40], [x0 + l3 // 2, y0 + longueur * 0.40]], np.int32)
    lunette = np.array([[x0 + l3 // 2, y0 + longueur * 0.72], [x1 - l3 // 2, y0 + longueur * 0.72],
                        [x1 - l3, y0 + longueur * 0.80], [x0 + l3, y0 + longueur * 0.80]], np.int32)
    cv2.fillPoly(calque, [pare_brise, lunette], vitre, cv2.LINE_AA)
    toit = tuple(int(min(255, c * 1.15 + 10)) for c in couleur)
    cv2.rectangle(calque, (x0 + l3, int(y0 + longueur * 0.42)), (x1 - l3, int(y0 + longueur * 0.70)), toit, -1)
    if rng.random() < 0.5:                                  # avant / arrière inversés
        calque, masque = calque[::-1].copy(), masque[::-1].copy()
    return calque, masque


def poser(img, ombres, carrosseries, calque, masque, centre, angle, rng):
    """Tourne la voiture, la colle au centre donné et renvoie sa boîte (x0, y0, x1, y1) en pixels."""
    h, w = masque.shape
    diag = int(math.hypot(w, h)) + 4
    M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    M[0, 2] += diag / 2 - w / 2
    M[1, 2] += diag / 2 - h / 2
    calque_r = cv2.warpAffine(calque, M, (diag, diag), flags=cv2.INTER_LINEAR)
    masque_r = cv2.warpAffine(masque, M, (diag, diag), flags=cv2.INTER_LINEAR)
    ox, oy = int(centre[0] - diag / 2), int(centre[1] - diag / 2)
    # Ombre portée : le masque décalé et flouté, assombrit le sol
    dx, dy = rng.randint(-6, 6), rng.randint(2, 8)
    ombre = cv2.GaussianBlur(masque_r, (0, 0), 3).astype(np.float32) / 255 * rng.uniform(0.25, 0.5)
    coller(ombres, ombre, ox + dx, oy + dy, mode="max")
    # Voiture
    alpha = masque_r.astype(np.float32)[..., None] / 255
    coller(img, calque_r.astype(np.float32) * alpha, ox, oy, alpha=alpha)
    coller(carrosseries, alpha[..., 0], ox, oy, mode="max")
    ys, xs = np.nonzero(masque_r > 127)
    if len(xs) == 0:
        return None
    return ox + xs.min(), oy + ys.min(), ox + xs.max() + 1, oy + ys.max() + 1


def coller(dest, source, ox, oy, alpha=None, mode="alpha"):
    """Colle `source` dans `dest` en (ox, oy), en coupant ce qui sort de l'image."""
    H, W = dest.shape[:2]
    h, w = source.shape[:2]
    x0, y0, x1, y1 = max(ox, 0), max(oy, 0), min(ox + w, W), min(oy + h, H)
    if x0 >= x1 or y0 >= y1:
        return
    s = source[y0 - oy:y1 - oy, x0 - ox:x1 - ox]
    if mode == "max":
        dest[y0:y1, x0:x1] = np.maximum(dest[y0:y1, x0:x1], s)
    else:
        a = alpha[y0 - oy:y1 - oy, x0 - ox:x1 - ox]
        dest[y0:y1, x0:x1] = dest[y0:y1, x0:x1] * (1 - a) + s


# ---------- Scénario d'une image ----------

def choisir_voitures(rng):
    """Liste de (centre_cm, angle, cas). Cas : place, cheval, zone_interdite, allee."""
    voitures = []
    if rng.random() < 0.06:                                  # parking vide (exemples négatifs)
        return voitures, "vide"
    taux = rng.uniform(0.2, 0.95)
    ids = list(PLACES_CM)
    occupees = [p for p in ids if rng.random() < taux]
    scenario = "normal"
    if rng.random() < 0.18:                                  # une voiture à cheval sur deux places
        rangee = rng.choice(["A", "B"])
        i = rng.randint(1, 3)
        gauche, droite = f"{rangee}{i}", f"{rangee}{i + 1}"
        occupees = [p for p in occupees if p not in (gauche, droite)]
        x, y, l, h = PLACES_CM[droite]
        voitures.append(((x + rng.uniform(-1.5, 1.5), y + h / 2 + rng.uniform(-1, 1)), rng.uniform(-12, 12), "cheval"))
        scenario = "cheval"
    for p in occupees:
        x, y, l, h = PLACES_CM[p]
        centre = (x + l / 2 + rng.uniform(-1.2, 1.2), y + h / 2 + rng.uniform(-1.5, 1.5))
        voitures.append((centre, rng.uniform(-10, 10) + rng.choice([0, 180]), "place"))
    if rng.random() < 0.18:                                  # voiture dans la zone interdite
        x, y, l, h = ZONE_INTERDITE_CM
        voitures.append(((x + l / 2 + rng.uniform(-1, 1), y + h / 2 + rng.uniform(-1, 1)), rng.uniform(-20, 20), "zone_interdite"))
        scenario = "zone_interdite" if scenario == "normal" else scenario + "+zone_interdite"
    if rng.random() < 0.15:                                  # voiture qui roule dans l'allée
        voitures.append(((rng.uniform(4, 46), 20 + rng.uniform(-1.5, 1.5)), 90 + rng.uniform(-15, 15), "allee"))
    return voitures, scenario


def eclairage_et_bruit(img, rng):
    """Lumière non uniforme, balance des couleurs, flou et bruit de capteur."""
    yy, xx = np.mgrid[0:HAUTEUR, 0:LARGEUR].astype(np.float32)
    angle = rng.uniform(0, 2 * math.pi)
    gradient = (xx * math.cos(angle) + yy * math.sin(angle)) / max(LARGEUR, HAUTEUR)
    lumiere = rng.uniform(0.75, 1.15) + rng.uniform(-0.25, 0.25) * (gradient - gradient.mean())
    img = img * lumiere[..., None]
    img = img * np.array([rng.uniform(0.9, 1.1) for _ in range(3)], np.float32)
    if rng.random() < 0.6:
        img = cv2.GaussianBlur(img, (0, 0), rng.uniform(0.3, 1.3))
    img += np.random.default_rng(rng.randint(0, 10**9)).normal(0, rng.uniform(1, 6), img.shape)
    return np.clip(img, 0, 255).astype(np.uint8)


def generer_image(rng):
    matrice, echelle = camera_aleatoire(rng)
    img = dessiner_sol(rng)
    dessiner_parking(img, matrice, echelle, rng)
    ombres = np.zeros((HAUTEUR, LARGEUR), np.float32)        # assombrissement du sol (0 à 0,5)
    carrosseries = np.zeros((HAUTEUR, LARGEUR), np.float32)  # 1 là où il y a une voiture
    voitures, scenario = choisir_voitures(rng)
    boites = []
    rotation_camera = math.degrees(math.atan2(matrice[1, 0], matrice[0, 0]))
    for centre_cm, angle, cas in voitures:
        centre = vers_pixels(matrice, [centre_cm])[0]
        calque, masque = dessiner_voiture(rng, echelle)
        boite = poser(img, ombres, carrosseries, calque, masque, centre, -(angle + rotation_camera), rng)
        if boite:
            boites.append((boite, cas))
    # Les ombres assombrissent le sol seulement, jamais une voiture
    img = img * (1 - ombres * (1 - carrosseries))[..., None]
    img = eclairage_et_bruit(img, rng)
    return img, boites, scenario


def ligne_yolo(boite):
    x0, y0, x1, y1 = boite
    x0, y0, x1, y1 = max(0, x0), max(0, y0), min(LARGEUR, x1), min(HAUTEUR, y1)
    if x1 - x0 < 4 or y1 - y0 < 4:                         # trop coupée par le bord : ignorée
        return None
    return f"0 {(x0 + x1) / 2 / LARGEUR:.6f} {(y0 + y1) / 2 / HAUTEUR:.6f} {(x1 - x0) / LARGEUR:.6f} {(y1 - y0) / HAUTEUR:.6f}"


def main():
    parser = argparse.ArgumentParser(description="Génère le lot 3 synthétique (maquette vue du dessus)")
    parser.add_argument("--nombre", type=int, default=300, help="nombre d'images")
    parser.add_argument("--sortie", default=str(SORTIE_PAR_DEFAUT), help="dossier du lot (défaut : iot/dataset/lot3)")
    parser.add_argument("--graine", type=int, default=3, help="même graine = mêmes images")
    args = parser.parse_args()

    sortie = Path(args.sortie)
    for sous in ("images", "labels"):
        if (sortie / sous).exists():
            shutil.rmtree(sortie / sous)                  # relancer remplace le lot, sans doublons
        (sortie / sous).mkdir(parents=True)

    rng = random.Random(args.graine)
    total_voitures = 0
    with open(sortie / "scenarios.csv", "w", newline="", encoding="utf-8") as f:
        journal = csv.writer(f)
        journal.writerow(["image", "scenario", "voitures", "cas"])
        for i in range(1, args.nombre + 1):
            nom = f"{PREFIXE}{i:05d}"
            img, boites, scenario = generer_image(rng)
            lignes = [l for l in (ligne_yolo(b) for b, _ in boites) if l]
            cv2.imwrite(str(sortie / "images" / f"{nom}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, rng.randint(70, 95)])
            (sortie / "labels" / f"{nom}.txt").write_text("\n".join(lignes) + ("\n" if lignes else ""), encoding="utf-8")
            journal.writerow([nom, scenario, len(lignes), " ".join(c for _, c in boites)])
            total_voitures += len(lignes)

    print(f"Images écrites : {args.nombre}")
    print(f"Voitures (boîtes) écrites : {total_voitures}")
    print(f"Dossier : {sortie}")


if __name__ == "__main__":
    main()
