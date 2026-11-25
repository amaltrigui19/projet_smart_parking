"""
Sprint 3 — Images synthétiques de la maquette, en attendant le matériel (lot 2).

La maquette n'est pas construite : on la DESSINE d'après le plan du contrat (docs/contrats.md §4) et on y
COLLE de vraies voitures miniatures, découpées dans les photos « vues du dessus » de toy-car-detection
(CC BY 4.0, citée dans iot/README.md). Comme on sait où chaque voiture est collée, les boîtes YOLO sont
exactes : aucune annotation à la main.

Ce que le générateur imite (iot/maquette.md) : plaque grise mate de 51 × 36 cm, lignes blanches de 6 mm,
zone interdite allee1 hachurée, pictogrammes PMR (A1) et électrique (B4), numéros des places, caméra
à la verticale en 640 × 480 (environ 12 px/cm), ombres et éclairage LED, petits défauts de cadrage.
Les séries A à H du protocole sont reprises : vide, normal, à cheval, zone interdite, places spéciales,
éclairage, angle, cas difficiles.

Trois commandes :
    decouper : photos « dessus » du lot 1 -> voitures détourées (PNG transparents) + planche de contrôle
    generer  : scènes -> images/ + labels/ + classes.txt + scenes.jsonl (vérité terrain : recouvrement de
               chaque place, zone interdite, homographie cm -> pixels), à passer dans preparer_datasets.py
    plan     : vue de la caméra à vide (calibration), places.json correspondant (contrat §5.2) et plan à
               imprimer à l'échelle 1 sur des feuilles A4 (maquette en papier)

Exemples (depuis la racine du dépôt) :
    python iot/annotation/generer_lot2.py decouper
    python iot/annotation/generer_lot2.py generer --nombre 300 --sortie iot/dataset/sources/synth_v1
    python iot/annotation/preparer_datasets.py --source iot/dataset/sources/synth_v1 --nom synth \\
        --sortie iot/dataset/lot2 --seuil-doublons 0
    python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot2 --apercu 12
    python iot/annotation/generer_lot2.py plan --sortie iot/dataset/synthetique/plan

Limite : ce sont des images de synthèse. Elles servent à démarrer l'entraînement ; le modèle doit ensuite
être évalué sur de vraies photos (maquette en papier, puis vraie maquette).
"""

import argparse
import csv
import json
import math
import shutil
import sys
from collections import Counter
from pathlib import Path

import cv2
import numpy as np
from shapely.geometry import Polygon, box as rectangle

ANNOTATION = Path(__file__).resolve().parent
DATASET = ANNOTATION.parent / "dataset"
DECOUPES_PAR_DEFAUT = DATASET / "synthetique" / "voitures"
EXCLUSIONS_DECOUPES = ANNOTATION / "exclusions_decoupes.txt"

# Plan du contrat (docs/contrats.md §4.1), en cm, origine en haut à gauche du cadre de 45 × 30
PLACES = {
    "A1": ((10, 0, 6, 10), "pmr", 120),
    "A2": ((16, 0, 6, 10), "normale", 120),
    "A3": ((22, 0, 6, 10), "normale", 120),
    "A4": ((28, 0, 6, 10), "normale", 120),
    "B1": ((10, 20, 6, 10), "normale", 120),
    "B2": ((16, 20, 6, 10), "normale", 120),
    "B3": ((22, 20, 6, 10), "normale", 120),
    "B4": ((28, 20, 6, 10), "electrique", 60),
}
ZONES_INTERDITES = {"allee1": (37, 10, 8, 10)}
PLAN = (45, 30)
MARGE = 3                        # la plaque déborde de 3 cm autour du plan : 51 × 36 cm
TABLE = (69, 54)                 # zone dessinée autour de la plaque (visible si la caméra bouge)
ORIGINE = ((TABLE[0] - PLAN[0]) / 2, (TABLE[1] - PLAN[1]) / 2)
IMAGE = (640, 480)
CHAMP_CM = 55                    # largeur vue par la caméra au réglage nominal (plaque entière + marge)
R = 20                           # résolution du dessin, en pixels par cm (l'image finale en a environ 12)

SERIES = {"A": 0.05, "B": 0.30, "C": 0.15, "D": 0.12, "E": 0.08, "F": 0.12, "G": 0.10, "H": 0.08}
TEINTES = {"rouge": 0, "orange": 11, "jaune": 25, "vert": 62, "cyan": 92, "bleu": 112, "violet": 140}
COULEURS = list(TEINTES) + ["blanc", "origine"]       # jamais de noir ni de gris foncé (contrat §4.4)
POLICE = cv2.FONT_HERSHEY_SIMPLEX


def px(x, y, r):
    """Point du plan (cm) -> pixel du dessin."""
    return ((x + ORIGINE[0]) * r, (y + ORIGINE[1]) * r)


def ipx(x, y, r):
    a, b = px(x, y, r)
    return int(round(a)), int(round(b))


def lire_liste(chemin):
    if not chemin.exists():
        return set()
    return {l.split("#")[0].strip() for l in chemin.read_text(encoding="utf-8").splitlines()} - {""}


def tourner(image, angle):
    """Tourne une image (avec canal alpha) autour de son centre, sans la couper."""
    h, w = image.shape[:2]
    cote = int(math.ceil(math.hypot(w, h))) + 2
    fond = np.zeros((cote, cote, image.shape[2]), image.dtype)
    y0, x0 = (cote - h) // 2, (cote - w) // 2
    fond[y0:y0 + h, x0:x0 + w] = image
    m = cv2.getRotationMatrix2D((cote / 2, cote / 2), angle, 1.0)
    return cv2.warpAffine(fond, m, (cote, cote), flags=cv2.INTER_LINEAR, borderValue=0)


# ---------------------------------------------------------------- decouper

def detourer(image, boite):
    """Masque de la voiture dans la boîte (GrabCut), trous bouchés, plus grande composante gardée."""
    masque = np.zeros(image.shape[:2], np.uint8)
    fond, objet = np.zeros((1, 65), np.float64), np.zeros((1, 65), np.float64)
    cv2.grabCut(image, masque, boite, fond, objet, 6, cv2.GC_INIT_WITH_RECT)
    m = np.where((masque == cv2.GC_FGD) | (masque == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))
    n, etiquettes, stats, _ = cv2.connectedComponentsWithStats(m)
    if n < 2:
        return None
    m = np.where(etiquettes == 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA])), 255, 0).astype(np.uint8)
    contours, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    plein = np.zeros_like(m)
    cv2.drawContours(plein, contours, -1, 255, cv2.FILLED)     # vitres et reflets : trous bouchés
    return plein


def redresser(bgra):
    """Tourne la voiture pour que sa longueur soit verticale, puis recadre au plus juste."""
    contours, _ = cv2.findContours((bgra[..., 3] > 127).astype(np.uint8), cv2.RETR_EXTERNAL,
                                   cv2.CHAIN_APPROX_SIMPLE)
    angle = cv2.minAreaRect(max(contours, key=cv2.contourArea))[2]
    meilleur = None
    for a in (angle, -angle, angle + 90, 90 - angle):
        t = tourner(bgra, a)
        ys, xs = np.nonzero(t[..., 3] > 127)
        h, w = ys.max() - ys.min() + 1, xs.max() - xs.min() + 1
        if h >= w and (meilleur is None or h * w < meilleur[0]):
            meilleur = (h * w, t[ys.min():ys.max() + 1, xs.min():xs.max() + 1])
    return meilleur[1] if meilleur else None


def damier(h, w):
    y, x = np.mgrid[0:h, 0:w]
    return np.where(((x // 10 + y // 10) % 2)[..., None] == 0, 205, 160).astype(np.uint8).repeat(3, 2)


def decouper(args):
    lot = Path(args.lot)
    vues = {r["origine"]: r["vue"] for r in csv.DictReader(
        (ANNOTATION / "vues_toycar.csv").open(encoding="utf-8"))}
    exclues = lire_liste(EXCLUSIONS_DECOUPES)
    sortie = Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)
    for ancien in sortie.glob("*.png"):
        ancien.unlink()

    vignettes, ecartees = [], []
    for chemin in sorted((lot / "images").glob("toycar_*")):
        origine = chemin.stem[len("toycar_"):]
        if vues.get(origine) != "dessus":
            continue
        image = cv2.imread(str(chemin))
        hauteur, largeur = image.shape[:2]
        lignes = [l.split() for l in (lot / "labels" / f"{chemin.stem}.txt").read_text(
            encoding="utf-8").splitlines() if len(l.split()) == 5]
        for k, v in enumerate(lignes):
            nom = f"{origine}_{k}"
            if nom in exclues:
                ecartees.append(nom)
                continue
            xc, yc, bw, bh = (float(t) for t in v[1:])
            marge = 0.06 * max(bw * largeur, bh * hauteur)
            x1 = max(0, int((xc - bw / 2) * largeur - marge))
            y1 = max(0, int((yc - bh / 2) * hauteur - marge))
            x2 = min(largeur - 1, int((xc + bw / 2) * largeur + marge))
            y2 = min(hauteur - 1, int((yc + bh / 2) * hauteur + marge))
            masque = detourer(image, (x1, y1, x2 - x1, y2 - y1))
            if masque is None:
                ecartees.append(f"{nom} (détourage vide)")
                continue
            bgra = np.dstack([image, masque])[y1:y2, x1:x2]
            voiture = redresser(bgra)
            if voiture is None:
                ecartees.append(f"{nom} (orientation)")
                continue
            voiture[..., 3] = cv2.GaussianBlur(voiture[..., 3], (0, 0), 0.8)
            remplissage = (voiture[..., 3] > 127).mean()
            cv2.imwrite(str(sortie / f"{nom}.png"), voiture)

            h = 150
            w = max(1, int(voiture.shape[1] * h / voiture.shape[0]))
            v = cv2.resize(voiture, (w, h), interpolation=cv2.INTER_AREA)
            a = v[..., 3:4] / 255.0
            tuile = np.full((h + 34, 130, 3), 255, np.uint8)
            fond = damier(h, w)
            x0 = max(0, (130 - w) // 2)
            w = min(w, 130)
            tuile[:h, x0:x0 + w] = (v[:, :w, :3] * a[:, :w] + fond[:, :w] * (1 - a[:, :w])).astype(np.uint8)
            alerte = (0, 0, 220) if not 0.7 <= remplissage <= 0.98 else (0, 0, 0)
            cv2.putText(tuile, nom[-14:], (2, h + 14), POLICE, 0.36, (0, 0, 0), 1, cv2.LINE_AA)
            cv2.putText(tuile, f"rempl. {remplissage:.2f}", (2, h + 29), POLICE, 0.36, alerte, 1, cv2.LINE_AA)
            vignettes.append(tuile)

    if not vignettes:
        sys.exit(f"Aucune voiture découpée : le lot {lot} contient-il les images toycar_ ?")
    while len(vignettes) % 8:
        vignettes.append(np.full_like(vignettes[0], 255))
    planche = np.vstack([np.hstack(vignettes[i:i + 8]) for i in range(0, len(vignettes), 8)])
    chemin_planche = sortie.parent / "planche_decoupes.jpg"
    cv2.imwrite(str(chemin_planche), planche)
    print(f"Voitures découpées : {len(list(sortie.glob('*.png')))} -> {sortie}")
    if ecartees:
        print(f"Écartées : {len(ecartees)} : {', '.join(ecartees)}")
    print(f"Planche de contrôle : {chemin_planche}")
    print(f"À regarder : une découpe ratée se met dans {EXCLUSIONS_DECOUPES.name} (nom_k  # raison), "
          "puis on relance decouper.")


# ---------------------------------------------------------------- dessin de la maquette

def texture(rng, h, w, taille, amplitude):
    petit = rng.normal(0, 1, (max(2, h // taille), max(2, w // taille))).astype(np.float32)
    return cv2.resize(petit, (w, h), interpolation=cv2.INTER_CUBIC) * amplitude


def ruban(img, a, b, couleur, r, epaisseur_cm, bruit):
    """Bande de ruban adhésif de a à b (cm), prolongée de 0,5 cm pour des coins bien fermés."""
    (xa, ya), (xb, yb) = a, b
    n = math.hypot(xb - xa, yb - ya) or 1
    ux, uy = (xb - xa) / n * 0.5, (yb - ya) / n * 0.5
    p = ipx(xa - ux + bruit(), ya - uy + bruit(), r)
    q = ipx(xb + ux + bruit(), yb + uy + bruit(), r)
    cv2.line(img, p, q, couleur, max(1, int(round(epaisseur_cm * r))), cv2.LINE_AA)


def pictogramme(img, centre, genre, r):
    cote = 3.0
    x0, y0 = px(centre[0] - cote / 2, centre[1] - cote / 2, r)
    s = cote * r
    cv2.rectangle(img, (int(x0), int(y0)), (int(x0 + s), int(y0 + s)), (242, 242, 242), -1)

    def p(u, v):
        return int(x0 + u * s), int(y0 + v * s)

    noir, e = (25, 25, 25), max(1, int(0.07 * s))
    if genre == "pmr":
        cv2.circle(img, p(.45, .17), max(1, int(.08 * s)), noir, -1)
        cv2.line(img, p(.43, .30), p(.43, .58), noir, e, cv2.LINE_AA)
        cv2.line(img, p(.43, .40), p(.66, .40), noir, e, cv2.LINE_AA)
        cv2.line(img, p(.43, .58), p(.68, .58), noir, e, cv2.LINE_AA)
        cv2.line(img, p(.68, .58), p(.76, .84), noir, e, cv2.LINE_AA)
        cv2.ellipse(img, p(.42, .68), (int(.2 * s), int(.2 * s)), 0, 40, 320, noir, e, cv2.LINE_AA)
    else:
        eclair = np.array([p(.60, .06), p(.28, .56), p(.50, .56), p(.40, .94), p(.74, .40),
                           p(.52, .40), p(.64, .06)], np.int32)
        cv2.fillPoly(img, [eclair], noir, cv2.LINE_AA)


def rendre_plaque(rng, r, sobre=False):
    """La maquette vide vue de dessus, posée sur une table. sobre=True : sans bruit ni hasard."""
    largeur, hauteur = int(TABLE[0] * r), int(TABLE[1] * r)
    if sobre:
        img = np.full((hauteur, largeur, 3), 90.0, np.float32)
        gris, teinte = 135.0, np.zeros(3)
    else:
        tables = [(70, 95, 130), (45, 45, 50), (185, 185, 180), (100, 125, 160), (150, 150, 150)]
        base = np.array(tables[int(rng.integers(len(tables)))], np.float32) + rng.normal(0, 8, 3)
        img = np.ones((hauteur, largeur, 3), np.float32) * base
        img += texture(rng, hauteur, largeur, int(4 * r), 10)[..., None]
        gris, teinte = rng.uniform(105, 150), rng.normal(0, 4, 3)
    x0, y0 = ipx(-MARGE, -MARGE, r)
    x1, y1 = ipx(PLAN[0] + MARGE, PLAN[1] + MARGE, r)
    plaque = np.ones((y1 - y0, x1 - x0, 3), np.float32) * (gris + teinte)
    if not sobre:
        plaque += texture(rng, y1 - y0, x1 - x0, int(2 * r), 4)[..., None]
        plaque += rng.normal(0, 2.5, (y1 - y0, x1 - x0, 1))
    img[y0:y1, x0:x1] = plaque
    img = np.clip(img, 0, 255).astype(np.uint8)

    def bruit():
        return 0.0 if sobre else rng.uniform(-0.12, 0.12)

    def blanc():
        return (240, 240, 240) if sobre else tuple(float(v) for v in rng.uniform(222, 250) + rng.normal(0, 3, 3))

    def epaisseur():
        return 0.6 if sobre else rng.uniform(0.5, 0.7)

    # Lignes des places (blanches, 6 mm), chaque segment commun dessiné une seule fois
    segments = set()
    for (x, y, w, h), _, _ in PLACES.values():
        for seg in (((x, y), (x + w, y)), ((x + w, y), (x + w, y + h)),
                    ((x, y + h), (x + w, y + h)), ((x, y), (x, y + h))):
            segments.add(tuple(sorted(seg)))
    for a, b in sorted(segments):
        ruban(img, a, b, blanc(), r, epaisseur(), bruit)

    # Zone interdite allee1 : hachures et bord en ruban de couleur
    x, y, w, h = ZONES_INTERDITES["allee1"]
    couleur = (0, 200, 235) if sobre or rng.random() < 0.5 else (50, 50, 205)    # jaune ou rouge (BGR)
    calque = img.copy()
    for k in np.arange(-h, w + h, 2.0):
        cv2.line(calque, ipx(x + k, y, r), ipx(x + k + h, y + h, r), couleur, max(1, int(0.6 * r)), cv2.LINE_AA)
    zone = np.zeros(img.shape[:2], np.uint8)
    cv2.rectangle(zone, ipx(x, y, r), ipx(x + w, y + h, r), 255, -1)
    img[zone > 0] = calque[zone > 0]
    for a, b in (((x, y), (x + w, y)), ((x, y + h), (x + w, y + h)), ((x, y), (x, y + h))):
        ruban(img, a, b, couleur, r, epaisseur(), bruit)

    pictogramme(img, (13, 2.5), "pmr", r)
    pictogramme(img, (31, 27.5), "electrique", r)

    # Numéros sur le bord extérieur, hors des places
    echelle, trait = 0.9 * r / 22, max(1, int(round(0.12 * r)))
    for pid, ((x, y, w, h), _, _) in PLACES.items():
        (tw, _), _ = cv2.getTextSize(pid, POLICE, echelle, trait)
        base = -1.0 if pid[0] == "A" else PLAN[1] + 2.2
        cx, cy = px(x + w / 2, base, r)
        cv2.putText(img, pid, (int(cx - tw / 2), int(cy)), POLICE, echelle, (35, 35, 35), trait, cv2.LINE_AA)
    return img


# ---------------------------------------------------------------- voitures et scènes

def recolorer(bgra, couleur, rng):
    """Change la couleur de la carrosserie (pixels saturés) ; vitres et pneus restent sombres."""
    if couleur == "origine":
        return bgra.copy()
    hsv = cv2.cvtColor(bgra[..., :3], cv2.COLOR_BGR2HSV).astype(np.float32)
    peinture = (hsv[..., 1] > 70) & (hsv[..., 2] > 50)
    if couleur in TEINTES:
        hsv[..., 0][peinture] = (TEINTES[couleur] + rng.normal(0, 2)) % 180
    elif couleur == "blanc":
        hsv[..., 1][peinture] *= 0.12
        hsv[..., 2][peinture] = 175 + hsv[..., 2][peinture] * 0.3
    elif couleur == "gris_clair":                     # série H : couleur proche du fond
        hsv[..., 1][peinture] *= 0.08
        hsv[..., 2][peinture] = 120 + hsv[..., 2][peinture] * 0.25
    hsv = np.clip(hsv, 0, [179, 255, 255]).astype(np.uint8)
    return np.dstack([cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR), bgra[..., 3]])


def empreinte(centre, angle, longueur, largeur):
    """Rectangle occupé au sol (cm), tourné comme la voiture collée par coller()."""
    m = cv2.getRotationMatrix2D((0, 0), angle, 1.0)[:, :2]
    coins = np.array([[-largeur / 2, -longueur / 2], [largeur / 2, -longueur / 2],
                      [largeur / 2, longueur / 2], [-largeur / 2, longueur / 2]])
    return Polygon(coins @ m.T + np.array(centre))


def coller(toile, voiture, centre, angle, r, ombre):
    """Colle la voiture (BGRA, longueur verticale) avec son ombre ; renvoie son contour (px du dessin)."""
    t = tourner(voiture, angle)
    cote = t.shape[0]
    cx, cy = px(*centre, r)
    x0, y0 = int(round(cx - cote / 2)), int(round(cy - cote / 2))
    if x0 < 0 or y0 < 0 or x0 + cote > toile.shape[1] or y0 + cote > toile.shape[0]:
        return None
    a = t[..., 3].astype(np.float32) / 255
    o = cv2.warpAffine(cv2.GaussianBlur(a, (0, 0), 0.35 * r), np.float32([[1, 0, ombre[0]], [0, 1, ombre[1]]]),
                       (cote, cote))
    zone = toile[y0:y0 + cote, x0:x0 + cote].astype(np.float32)
    zone *= 1 - 0.45 * o[..., None]
    zone = zone * (1 - a[..., None]) + t[..., :3].astype(np.float32) * a[..., None]
    toile[y0:y0 + cote, x0:x0 + cote] = np.clip(zone, 0, 255).astype(np.uint8)
    contours, _ = cv2.findContours((t[..., 3] > 127).astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    return np.vstack(contours).reshape(-1, 2).astype(np.float32) + (x0, y0)


def angle_place(rng, ecart=10):
    return float(rng.choice([0, 180]) + np.clip(rng.normal(0, 4), -ecart, ecart))


def dans_place(pid, rng, decalage=0.0):
    (x, y, w, h), _, _ = PLACES[pid]
    return (x + w / 2 + decalage + rng.uniform(-0.6, 0.6), y + h / 2 + rng.uniform(-0.9, 0.9)), angle_place(rng), "place"


def scene(serie, rng):
    """Voitures à poser pour une série du protocole : [(centre_cm, angle, rôle)]."""
    if serie == "A":
        return []
    poses, prises = [], set()
    if serie == "C":                                  # à cheval sur deux places voisines
        for _ in range(int(rng.integers(1, 3))):
            rangee, k = str(rng.choice(["A", "B"])), int(rng.integers(1, 4))
            (x, y, w, h), _, _ = PLACES[f"{rangee}{k}"]
            poses.append(((x + w + rng.uniform(-1.3, 1.3), y + h / 2 + rng.uniform(-0.8, 0.8)),
                          float(rng.choice([0, 180]) + rng.uniform(-25, 25)), "cheval"))
            prises |= {f"{rangee}{k}", f"{rangee}{k + 1}"}
    if serie == "D":                                  # dans allee1, entière ou à moitié
        x, y, w, h = ZONES_INTERDITES["allee1"]
        cx = rng.uniform(40.5, 41.5) if rng.random() < 0.6 else rng.uniform(35.5, 38)
        poses.append(((cx, rng.uniform(y + 2.5, y + h - 2.5)),
                      float(rng.choice([90, 270]) + rng.uniform(-15, 15)), "zone_interdite"))
    if serie == "E":                                  # places spéciales occupées
        for pid in [p for p in ("A1", "B4") if rng.random() < 0.7] or ["A1"]:
            poses.append(dans_place(pid, rng))
            prises.add(pid)
    if serie == "H":                                  # voitures très proches
        rangee, k = str(rng.choice(["A", "B"])), int(rng.integers(1, 4))
        poses.append(dans_place(f"{rangee}{k}", rng, decalage=1.0))
        poses.append(dans_place(f"{rangee}{k + 1}", rng, decalage=-1.0))
        prises |= {f"{rangee}{k}", f"{rangee}{k + 1}"}
    if serie in "BEFGH" and rng.random() < (0.5 if serie == "H" else 0.12):    # voiture qui roule dans l'allée
        poses.append(((rng.uniform(3, 33), rng.uniform(12.5, 17.5)),
                      float(rng.choice([90, 270]) + rng.uniform(-8, 8)), "allee"))
    n = int(rng.integers(0, 6)) if serie in "CDEH" else int(rng.choice([1, 2, 3, 4, 5, 6, 7, 8]))
    libres = [p for p in rng.permutation(list(PLACES)) if p not in prises]
    poses += [dans_place(str(p), rng) for p in libres[:n]]
    return poses


def camera(rng, r, forte=False, nominale=False):
    """Homographie dessin -> image 640 × 480 : caméra au-dessus du centre, avec de petits défauts."""
    if nominale:
        s, d, rot, j = 1.0, 0.0, 0.0, 0.0
    elif forte:                                       # série G : caméra volontairement décalée
        s, d, rot, j = rng.uniform(0.96, 1.15), 3.0, 6.0, 2.0
    else:
        s, d, rot, j = rng.uniform(0.97, 1.06), 1.0, 2.0, 0.5
    largeur = CHAMP_CM * s
    hauteur = largeur * IMAGE[1] / IMAGE[0]
    cx = PLAN[0] / 2 + (rng.uniform(-d, d) if d else 0)
    cy = PLAN[1] / 2 + (rng.uniform(-d, d) if d else 0)
    a = math.radians(rng.uniform(-rot, rot) if rot else 0)
    source = []
    for x, y in ((-largeur / 2, -hauteur / 2), (largeur / 2, -hauteur / 2),
                 (largeur / 2, hauteur / 2), (-largeur / 2, hauteur / 2)):
        dx, dy = (rng.uniform(-j, j), rng.uniform(-j, j)) if j else (0, 0)
        source.append(px(cx + x * math.cos(a) - y * math.sin(a) + dx, cy + x * math.sin(a) + y * math.cos(a) + dy, r))
    cible = [(0, 0), (IMAGE[0], 0), (IMAGE[0], IMAGE[1]), (0, IMAGE[1])]
    return cv2.getPerspectiveTransform(np.float32(source), np.float32(cible))


def photographier(toile, h, r):
    """Projette le dessin sur le capteur (avec un léger flou contre le crénelage)."""
    sigma = 0.45 * r * CHAMP_CM / IMAGE[0]
    return cv2.warpPerspective(cv2.GaussianBlur(toile, (0, 0), sigma), h, IMAGE,
                               flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)


def eclairer(image, rng, fort):
    """Éclairage LED (dégradé, vignettage, teinte), bruit du capteur, flou, compression JPEG."""
    h, w = image.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    xx, yy = xx / w - 0.5, yy / h - 0.5
    a = rng.uniform(0, 2 * math.pi)
    degrade = 1 + (rng.uniform(0.15, 0.4) if fort else rng.uniform(0.02, 0.12)) * (xx * math.cos(a) + yy * math.sin(a))
    vignette = 1 - rng.uniform(0, 0.2) * (xx ** 2 + yy ** 2) * 2
    lumiere = rng.uniform(0.6, 1.3) if fort else rng.uniform(0.9, 1.1)
    teinte = 1 + rng.normal(0, 0.06 if fort else 0.025, 3)
    img = image.astype(np.float32) * (degrade * vignette * lumiere)[..., None] * teinte
    if rng.random() < 0.6:
        img = cv2.GaussianBlur(img, (0, 0), rng.uniform(0.3, 0.9))
    img += rng.normal(0, rng.uniform(1, 5), img.shape)
    img = np.clip(img, 0, 255).astype(np.uint8)
    ok, jpeg = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, int(rng.integers(70, 96))])
    return cv2.imdecode(jpeg, cv2.IMREAD_COLOR)


def matrice_cm(h, r):
    """Homographie plan (cm) -> image (px), pour retrouver les polygones des places."""
    return h @ np.array([[r, 0, ORIGINE[0] * r], [0, r, ORIGINE[1] * r], [0, 0, 1]])


def projeter(h_cm, rect):
    x, y, w, hh = rect
    coins = np.float32([[x, y], [x + w, y], [x + w, y + hh], [x, y + hh]]).reshape(-1, 1, 2)
    return [[int(round(a)), int(round(b))] for a, b in cv2.perspectiveTransform(coins, h_cm).reshape(-1, 2)]


def composer(serie, rng, decoupes):
    toile = rendre_plaque(rng, R)
    ombre = rng.uniform(-0.4, 0.4, 2) * R
    poses, emprises, voitures = scene(serie, rng), [], []
    for centre, angle, role in poses:
        nom, bgra = decoupes[int(rng.integers(len(decoupes)))]
        couleur = "gris_clair" if serie == "H" and rng.random() < 0.4 else str(rng.choice(COULEURS))
        longueur = rng.uniform(6.3, 7.8)                  # 1/64, environ 7 cm
        largeur = longueur * rng.uniform(0.42, 0.5)
        emp = empreinte(centre, angle, longueur, largeur)
        if any(emp.buffer(0.2).intersects(e) for e in emprises):
            continue                                  # deux voitures ne se chevauchent jamais
        v = cv2.resize(recolorer(bgra, couleur, rng), (max(2, round(largeur * R)), max(2, round(longueur * R))),
                       interpolation=cv2.INTER_AREA)
        contour = coller(toile, v, centre, angle, R, ombre)
        if contour is None:
            continue
        emprises.append(emp)
        voitures.append({"decoupe": nom, "couleur": couleur, "role": role,
                         "centre_cm": [round(c, 2) for c in centre], "angle": round(angle, 1),
                         "taille_cm": [round(longueur, 2), round(largeur, 2)],
                         "recouvrement": {p: round(emp.intersection(rectangle(x, y, x + w, y + h)).area / emp.area, 3)
                                          for p, ((x, y, w, h), _, _) in PLACES.items()
                                          if emp.intersects(rectangle(x, y, x + w, y + h))},
                         "zone_interdite": {z: round(emp.intersection(rectangle(x, y, x + w, y + h)).area / emp.area, 3)
                                            for z, (x, y, w, h) in ZONES_INTERDITES.items()
                                            if emp.intersects(rectangle(x, y, x + w, y + h))},
                         "_contour": contour})
    h = camera(rng, R, forte=(serie == "G"))
    image = eclairer(photographier(toile, h, R), rng, fort=(serie == "F"))
    return image, voitures, h


def generer(args):
    rng = np.random.default_rng(args.graine)
    decoupes = [(f.stem, cv2.imread(str(f), cv2.IMREAD_UNCHANGED)) for f in sorted(Path(args.decoupes).glob("*.png"))]
    if not decoupes:
        sys.exit(f"Aucune voiture dans {args.decoupes} : lancer d'abord la commande decouper")
    sortie = Path(args.sortie)
    if sortie.exists() and any(sortie.iterdir()):
        if not args.remplacer:
            sys.exit(f"{sortie} n'est pas vide : ajouter --remplacer ou choisir un autre dossier")
        shutil.rmtree(sortie)
    for sous in ("images", "labels"):
        (sortie / sous).mkdir(parents=True, exist_ok=True)
    (sortie / "classes.txt").write_text("voiture\n", encoding="utf-8")

    series, poids = list(SERIES), np.array(list(SERIES.values()))
    stats, hors_image = Counter(), 0
    with (sortie / "scenes.jsonl").open("w", encoding="utf-8") as journal:
        for i in range(args.nombre):
            serie = str(rng.choice(series, p=poids / poids.sum()))
            nom = f"s{args.graine}_{i:05d}"
            image, voitures, h = composer(serie, rng, decoupes)
            lignes = []
            for v in voitures:
                pts = cv2.perspectiveTransform(v.pop("_contour").reshape(-1, 1, 2), h).reshape(-1, 2)
                (x1, y1), (x2, y2) = pts.min(0), pts.max(0)
                cx1, cy1 = max(0.0, x1), max(0.0, y1)
                cx2, cy2 = min(float(IMAGE[0]), x2), min(float(IMAGE[1]), y2)
                visible = max(0.0, cx2 - cx1) * max(0.0, cy2 - cy1) / max(1e-6, (x2 - x1) * (y2 - y1))
                if visible < 0.3:                     # presque entièrement hors de l'image : pas de boîte
                    v["boite_px"] = None
                    hors_image += 1
                    continue
                v["boite_px"] = [round(float(c), 1) for c in (cx1, cy1, cx2, cy2)]
                lignes.append(f"0 {(cx1 + cx2) / 2 / IMAGE[0]:.6f} {(cy1 + cy2) / 2 / IMAGE[1]:.6f} "
                              f"{(cx2 - cx1) / IMAGE[0]:.6f} {(cy2 - cy1) / IMAGE[1]:.6f}")
            cv2.imwrite(str(sortie / "images" / f"{nom}.jpg"), image, [cv2.IMWRITE_JPEG_QUALITY, 95])
            (sortie / "labels" / f"{nom}.txt").write_text("\n".join(lignes) + ("\n" if lignes else ""),
                                                         encoding="utf-8")
            journal.write(json.dumps({"image": f"{nom}.jpg", "serie": serie, "voitures": voitures,
                                      "homographie_cm_vers_px": np.round(matrice_cm(h, R), 6).tolist()},
                                     ensure_ascii=False) + "\n")
            stats[serie] += 1
            stats["voitures"] += len(lignes)
            if (i + 1) % 50 == 0:
                print(f"  {i + 1}/{args.nombre} images", flush=True)

    print(f"=== {args.nombre} images synthétiques -> {sortie} (graine {args.graine}) ===")
    print("Séries : " + ", ".join(f"{s} {stats[s]}" for s in series))
    print(f"Voitures (boîtes) : {stats['voitures']}" + (f" | hors de l'image, sans boîte : {hors_image}" if hors_image else ""))
    print(f"Vérité terrain (places recouvertes, zone interdite, homographie) : {sortie / 'scenes.jsonl'}")


# ---------------------------------------------------------------- plan

def plan(args):
    sortie = Path(args.sortie)
    sortie.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(0)

    h = camera(rng, R, nominale=True)
    cv2.imwrite(str(sortie / "vue_camera_vide.jpg"), photographier(rendre_plaque(rng, R, sobre=True), h, R),
                [cv2.IMWRITE_JPEG_QUALITY, 95])
    h_cm = matrice_cm(h, R)
    places = {
        "parking": "P1",
        "resolution": list(IMAGE),
        "places": [{"id": p, "type": t, "duree_max_min": d, "poly": projeter(h_cm, rect)}
                   for p, (rect, t, d) in PLACES.items()],
        "zones_interdites": [{"id": z, "poly": projeter(h_cm, rect)} for z, rect in ZONES_INTERDITES.items()],
    }
    (sortie / "places.json").write_text(json.dumps(places, indent=2, ensure_ascii=False), encoding="utf-8")

    # Plan à l'échelle 1, découpé en feuilles A4 paysage (marges de 1 cm), à imprimer « taille réelle »
    from PIL import Image
    r = args.dpi / 2.54
    toile = rendre_plaque(rng, r, sobre=True)
    x0, y0 = ipx(-MARGE, -MARGE, r)
    x1, y1 = ipx(PLAN[0] + MARGE, PLAN[1] + MARGE, r)
    plaque = toile[y0:y1, x0:x1]
    zone_l, zone_h, marge = 27.7, 19.0, 1.0
    colonnes = math.ceil((PLAN[0] + 2 * MARGE) / zone_l)
    lignes = math.ceil((PLAN[1] + 2 * MARGE) / zone_h)
    page_l, page_h = int(round(29.7 * r)), int(round(21.0 * r))
    pages = []
    for i in range(lignes):
        for j in range(colonnes):
            morceau = plaque[int(i * zone_h * r):int((i + 1) * zone_h * r), int(j * zone_l * r):int((j + 1) * zone_l * r)]
            page = np.full((page_h, page_l, 3), 255, np.uint8)
            m = int(marge * r)
            page[m:m + morceau.shape[0], m:m + morceau.shape[1]] = morceau
            cv2.rectangle(page, (m, m), (m + morceau.shape[1], m + morceau.shape[0]), (0, 0, 0), 1)
            texte = (f"Plan maquette 1:1 - feuille {i * colonnes + j + 1}/{lignes * colonnes} - ligne {i + 1}, "
                     f"colonne {j + 1} - imprimer a 100 % (taille reelle)")
            cv2.putText(page, texte, (m, int(0.65 * r)), POLICE, 0.011 * r, (0, 0, 0), 1, cv2.LINE_AA)
            regle_x = page_l - m - int(10 * r)                    # règle de 10 cm pour vérifier l'échelle
            cv2.line(page, (regle_x, int(0.5 * r)), (regle_x + int(10 * r), int(0.5 * r)), (0, 0, 0), 2)
            for k in range(11):
                cv2.line(page, (regle_x + int(k * r), int(0.3 * r)), (regle_x + int(k * r), int(0.5 * r)), (0, 0, 0), 1)
            pages.append(Image.fromarray(cv2.cvtColor(page, cv2.COLOR_BGR2RGB)))
    pdf = sortie / "plan_maquette_A4.pdf"
    pages[0].save(pdf, save_all=True, append_images=pages[1:], resolution=args.dpi)

    print(f"Vue de la caméra à vide (calibration) : {sortie / 'vue_camera_vide.jpg'}")
    print(f"Polygones correspondants (contrat §5.2) : {sortie / 'places.json'}")
    print(f"Plan à imprimer : {pdf} ({len(pages)} feuilles A4 paysage, à assembler ; la règle doit mesurer 10 cm)")


def main():
    parser = argparse.ArgumentParser(description="Images synthétiques de la maquette (lot 2)")
    sous = parser.add_subparsers(dest="commande", required=True)

    d = sous.add_parser("decouper", help="voitures vues du dessus du lot 1 -> PNG transparents")
    d.add_argument("--lot", default=str(DATASET / "lot1"), help="lot contenant les images toycar_")
    d.add_argument("--sortie", default=str(DECOUPES_PAR_DEFAUT), help="dossier des voitures découpées")
    d.set_defaults(fonction=decouper)

    g = sous.add_parser("generer", help="scènes synthétiques au format YOLO + vérité terrain")
    g.add_argument("--nombre", type=int, default=300, help="nombre d'images")
    g.add_argument("--sortie", required=True, help="dossier produit (images/, labels/, classes.txt, scenes.jsonl)")
    g.add_argument("--decoupes", default=str(DECOUPES_PAR_DEFAUT), help="dossier des voitures découpées")
    g.add_argument("--graine", type=int, default=0, help="même graine = mêmes images")
    g.add_argument("--remplacer", action="store_true", help="vider le dossier de sortie s'il existe")
    g.set_defaults(fonction=generer)

    p = sous.add_parser("plan", help="vue à vide, places.json et plan à imprimer")
    p.add_argument("--sortie", default=str(DATASET / "synthetique" / "plan"), help="dossier produit")
    p.add_argument("--dpi", type=int, default=150, help="résolution du PDF à imprimer")
    p.set_defaults(fonction=plan)

    args = parser.parse_args()
    args.fonction(args)


if __name__ == "__main__":
    main()
