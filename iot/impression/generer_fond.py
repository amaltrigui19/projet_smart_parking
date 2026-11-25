"""Génère le fond imprimable de la maquette (contrat §4.1, iot/maquette.md §2).

Plan de 45 x 30 cm (places de 6 x 10 cm pour des voitures au 1/64) et 3 cm de marge : feuille de 51 x 36 cm.

Sorties, à l'échelle 1:1 (1 cm sur le plan = 1 cm sur le papier) :
  - fond_maquette_A2.pdf         : une page A2 paysage (59,4 x 42 cm), la feuille de 51 x 36 cm centrée
                                   avec des traits de coupe ; chez un imprimeur ou un copy-shop ;
  - fond_maquette_tuiles_A4.pdf  : la même feuille en 4 pages A4 paysage, à assembler bord à bord ;
  - apercu.png                   : aperçu basse résolution.

Usage (depuis la racine du dépôt) :
    python iot/impression/generer_fond.py
"""

from pathlib import Path

import cv2
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.backends.backend_pdf import PdfPages
from matplotlib.patches import Arc, Circle, FancyArrow, Polygon, Rectangle
from shapely.geometry import Polygon as ShapelyPolygon
from shapely.geometry import box

DOSSIER = Path(__file__).resolve().parent

# --- Géométrie du contrat §4.1 (cm, origine en haut à gauche, y vers le bas) ---
LARGEUR_CM, HAUTEUR_CM = 45, 30
MARGE_CM = 3
PLACES = {  # id: (x, y, largeur, hauteur)
    "A1": (10, 0, 6, 10), "A2": (16, 0, 6, 10), "A3": (22, 0, 6, 10), "A4": (28, 0, 6, 10),
    "B1": (10, 20, 6, 10), "B2": (16, 20, 6, 10), "B3": (22, 20, 6, 10), "B4": (28, 20, 6, 10),
}
ALLEE_Y = (10, 20)
ZONE_INTERDITE = (37, 10, 8, 10)  # allee1
PLACES_X = (10, 34)               # les places occupent x de 10 à 34 ; le reste, hors allée, est du trottoir
TROTTOIRS = [(0, 0, 10, 10), (34, 0, 11, 10), (0, 20, 10, 10), (34, 20, 11, 10)]

LIGNE_CM = 0.6          # largeur des lignes blanches
PICTO_CM = 3.0          # côté des pictogrammes PMR et électrique
PX_PAR_CM = 60          # résolution de la texture (≈ 152 dpi)
CM_EN_POUCES = 1 / 2.54
PT_PAR_CM = 72 / 2.54   # 1 cm en points typographiques

BLANC = "#f4f4f0"
JAUNE = "#f2c218"
BLEU_PMR = "#1f5fbf"
VERT_ELEC = "#1f9a4a"

X0, Y0 = -MARGE_CM, -MARGE_CM
X1, Y1 = LARGEUR_CM + MARGE_CM, HAUTEUR_CM + MARGE_CM
Y_MILIEU = sum(ALLEE_Y) / 2


def texture() -> np.ndarray:
    """Image RGB (uint8) de toute la feuille : marge, trottoirs et asphalte gris moyen mat."""
    rng = np.random.default_rng(43)
    h, w = int((Y1 - Y0) * PX_PAR_CM), int((X1 - X0) * PX_PAR_CM)

    def px(x, y):
        return int(round((x - X0) * PX_PAR_CM)), int(round((y - Y0) * PX_PAR_CM))

    def bruit(sigma_flou, amplitude):
        b = rng.normal(0, 1, (h, w)).astype(np.float32)
        if sigma_flou:
            b = cv2.GaussianBlur(b, (0, 0), sigma_flou)
            b /= b.std() + 1e-6
        return b * amplitude

    # Marge : gris clair uni, légèrement granuleux.
    img = np.full((h, w), 212, np.float32) + bruit(0, 2)

    # Asphalte sur tout le plan : gris moyen, grain fin + variations douces + quelques gravillons.
    asphalte = 108 + bruit(0, 7) + bruit(1.2, 4) + bruit(30, 4)
    gravillons = rng.random((h, w)) < 0.004
    asphalte[gravillons] += rng.normal(25, 10, gravillons.sum())
    (ax0, ay0), (ax1, ay1) = px(0, 0), px(LARGEUR_CM, HAUTEUR_CM)
    img[ay0:ay1, ax0:ax1] = asphalte[ay0:ay1, ax0:ax1]

    # Trottoirs : béton clair en dalles de 5 cm.
    beton = 172 + bruit(0, 4) + bruit(20, 3)
    joint = max(1, int(0.1 * PX_PAR_CM))
    for (x, y, l, ht) in TROTTOIRS:
        (tx0, ty0), (tx1, ty1) = px(x, y), px(x + l, y + ht)
        img[ty0:ty1, tx0:tx1] = beton[ty0:ty1, tx0:tx1]
        for xx in np.arange(x, x + l + 0.01, 5):
            cx = px(xx, 0)[0]
            img[ty0:ty1, max(tx0, cx - joint // 2):min(tx1, cx + joint // 2 + 1)] -= 35
        for yy in np.arange(y, y + ht + 0.01, 5):
            cy = px(0, yy)[1]
            img[max(ty0, cy - joint // 2):min(ty1, cy + joint // 2 + 1), tx0:tx1] -= 35

    gris = np.clip(img, 0, 255).astype(np.uint8)
    return np.stack([gris, gris, np.clip(gris.astype(np.int16) + 2, 0, 255).astype(np.uint8)], axis=-1)


def fs(hauteur_cm: float) -> float:
    """Taille de police (pt) pour des majuscules d'environ `hauteur_cm` de haut."""
    return hauteur_cm * PT_PAR_CM / 0.73


def pictogramme_pmr(ax, cx, cy, taille):
    x0, y0 = cx - taille / 2, cy - taille / 2
    ax.add_patch(Rectangle((x0, y0), taille, taille, fc=BLEU_PMR, ec="none"))

    def p(u, v):
        return x0 + u * taille, y0 + v * taille

    ep = taille * 0.075 * PT_PAR_CM
    ax.add_patch(Circle(p(0.50, 0.17), 0.085 * taille, fc=BLANC, ec="none"))
    for a, b in [((0.48, 0.30), (0.44, 0.60)), ((0.44, 0.60), (0.70, 0.60)),
                 ((0.70, 0.60), (0.80, 0.84)), ((0.46, 0.42), (0.66, 0.42))]:
        ax.plot(*zip(p(*a), p(*b)), color=BLANC, lw=ep, solid_capstyle="round")
    ax.add_patch(Arc(p(0.40, 0.66), 0.46 * taille, 0.46 * taille, theta1=70, theta2=330,
                     color=BLANC, lw=ep))


def pictogramme_electrique(ax, cx, cy, taille):
    x0, y0 = cx - taille / 2, cy - taille / 2
    ax.add_patch(Rectangle((x0, y0), taille, taille, fc=VERT_ELEC, ec="none"))
    eclair = [(0.60, 0.08), (0.26, 0.55), (0.47, 0.55), (0.38, 0.92), (0.76, 0.42), (0.54, 0.42), (0.66, 0.08)]
    ax.add_patch(Polygon([(x0 + u * taille, y0 + v * taille) for u, v in eclair], fc=BLANC, ec="none"))


def dessiner(ax, img):
    if img is not None:
        ax.imshow(img, extent=(X0, X1, Y1, Y0), interpolation="bilinear", zorder=0)

    # Lignes des places : centrées sur les séparations, à l'extérieur sur le fond de rangée.
    separations = sorted({x for (x, _, _, _) in PLACES.values()} | {PLACES_X[1]})
    rangees = {"A": (0, ALLEE_Y[0], -LIGNE_CM), "B": (ALLEE_Y[1], HAUTEUR_CM, HAUTEUR_CM)}
    for y_haut, y_bas, y_fond in rangees.values():
        for x in separations:
            ax.add_patch(Rectangle((x - LIGNE_CM / 2, y_haut), LIGNE_CM, y_bas - y_haut, fc=BLANC, ec="none"))
        ax.add_patch(Rectangle((PLACES_X[0] - LIGNE_CM / 2, y_fond), PLACES_X[1] - PLACES_X[0] + LIGNE_CM,
                               LIGNE_CM, fc=BLANC, ec="none"))

    # Zone interdite allee1 : hachures jaunes à 45°, cadre jaune.
    zx, zy, zl, zh = ZONE_INTERDITE
    cadre = box(zx, zy, zx + zl, zy + zh)
    for d in np.arange(-zh, zl + zh, 2.0):
        bande = ShapelyPolygon([(zx + d, zy + zh), (zx + d + 0.8, zy + zh),
                                (zx + d + 0.8 + zh, zy), (zx + d + zh, zy)]).intersection(cadre)
        if bande.geom_type == "Polygon" and bande.area > 0:
            ax.add_patch(Polygon(list(bande.exterior.coords), fc=JAUNE, ec="none"))
    e = 0.4
    ax.add_patch(Rectangle((zx + e / 2, zy + e / 2), zl - e, zh - e, fc="none", ec=JAUNE, lw=e * PT_PAR_CM))

    # Places spéciales : pictogrammes au fond de la place, loin de l'allée.
    xa, ya, la, _ = PLACES["A1"]
    pictogramme_pmr(ax, xa + la / 2, ya + 0.6 + PICTO_CM / 2, PICTO_CM)
    xb, yb, lb, hb = PLACES["B4"]
    pictogramme_electrique(ax, xb + lb / 2, yb + hb - 0.6 - PICTO_CM / 2, PICTO_CM)

    # Flèches de sens de circulation (entrée à gauche, sortie à droite).
    for x in (1.5, 20.0):
        ax.add_patch(FancyArrow(x, Y_MILIEU, 4, 0, width=0.8, head_width=2.0, head_length=1.5,
                                length_includes_head=True, fc=BLANC, ec="none"))

    # Repères dans la marge : numéros des places, entrée / sortie.
    police = dict(fontfamily="DejaVu Sans", fontweight="bold", ha="center", va="center", clip_on=True)
    for pid, (x, y, l, h) in PLACES.items():
        ty = -1.8 if pid[0] == "A" else HAUTEUR_CM + 1.8
        ax.text(x + l / 2, ty, pid, fontsize=fs(0.9), color="#222222", **police)
    ax.text(-1.5, Y_MILIEU, "ENTRÉE", rotation=90, fontsize=fs(0.8), color="#222222", **police)
    ax.text(LARGEUR_CM + 1.5, Y_MILIEU, "SORTIE", rotation=-90, fontsize=fs(0.8), color="#222222", **police)

    # Repères d'angle (aident la calibration et la pose de la caméra).
    bras, ep = 1.5, 0.2
    for cx, cy, sx, sy in [(0, 0, -1, -1), (LARGEUR_CM, 0, 1, -1), (0, HAUTEUR_CM, -1, 1),
                           (LARGEUR_CM, HAUTEUR_CM, 1, 1)]:
        ax.add_patch(Rectangle((cx, cy), sx * bras, sy * ep, fc="#111111", ec="none"))
        ax.add_patch(Rectangle((cx, cy), sx * ep, sy * bras, fc="#111111", ec="none"))

    # Barre d'échelle de 5 cm pour vérifier l'impression à la règle (tient dans la marge, à gauche de B1).
    bx, by = 1.0, HAUTEUR_CM + 1.2
    ax.add_patch(Rectangle((bx, by), 5, 0.25, fc="#111111", ec="none"))
    for i in range(6):
        hauteur = 0.6 if i in (0, 5) else 0.4
        ax.add_patch(Rectangle((bx + i - 0.03, by + 0.25 - hauteur), 0.06, hauteur, fc="#111111", ec="none"))
    ax.text(bx + 2.5, by + 0.85, "5 cm : vérifier à la règle", fontsize=fs(0.25), color="#111111",
            fontfamily="DejaVu Sans", ha="center", va="center", clip_on=True)
    ax.text(LARGEUR_CM, HAUTEUR_CM + 2.6,
            f"Maquette P1 · plan {LARGEUR_CM} × {HAUTEUR_CM} cm · contrat §4.1 · imprimer à 100 %",
            fontsize=fs(0.22), color="#444444", fontfamily="DejaVu Sans", ha="right", va="center", clip_on=True)


def nouvel_axe(fig, rect, xlim, ylim):
    ax = fig.add_axes(rect)
    ax.set_xlim(*xlim)
    ax.set_ylim(*ylim)
    ax.set_aspect("equal")
    ax.axis("off")
    return ax


def traits_de_coupe(ax, longueur=1.5, ecart=0.3):
    """Traits fins dans le blanc de la page, dans le prolongement des bords de la feuille."""
    for x in (X0, X1):
        for y, s in ((Y0, -1), (Y1, 1)):
            ax.plot([x, x], [y + s * ecart, y + s * (ecart + longueur)], color="#000000", lw=0.5)
    for y in (Y0, Y1):
        for x, s in ((X0, -1), (X1, 1)):
            ax.plot([x + s * ecart, x + s * (ecart + longueur)], [y, y], color="#000000", lw=0.5)


def page_a2(img):
    page_l, page_h = 59.4, 42.0
    mx, my = (page_l - (X1 - X0)) / 2, (page_h - (Y1 - Y0)) / 2
    fig = plt.figure(figsize=(page_l * CM_EN_POUCES, page_h * CM_EN_POUCES))
    ax = nouvel_axe(fig, [0, 0, 1, 1], (X0 - mx, X1 + mx), (Y1 + my, Y0 - my))
    dessiner(ax, img)
    traits_de_coupe(ax)
    ax.text(X0, Y0 - my / 2, "Couper sur les traits : feuille de 51 × 36 cm. Imprimer en A2, à 100 % (taille réelle).",
            fontsize=8, color="#555555", fontfamily="DejaVu Sans", ha="left", va="center")
    fig.savefig(DOSSIER / "fond_maquette_A2.pdf", dpi=PX_PAR_CM * 2.54)
    plt.close(fig)


def apercu(img):
    l, h = X1 - X0, Y1 - Y0
    fig = plt.figure(figsize=(l * CM_EN_POUCES, h * CM_EN_POUCES))
    ax = nouvel_axe(fig, [0, 0, 1, 1], (X0, X1), (Y1, Y0))
    dessiner(ax, img)
    fig.savefig(DOSSIER / "apercu.png", dpi=50)
    plt.close(fig)


def tuiles_a4(img):
    page_l, page_h, marge = 29.7, 21.0, 1.0          # A4 paysage
    zone_l, zone_h = page_l - 2 * marge, page_h - 2 * marge
    l, h = X1 - X0, Y1 - Y0
    colonnes, lignes = int(np.ceil(l / zone_l)), int(np.ceil(h / zone_h))
    with PdfPages(DOSSIER / "fond_maquette_tuiles_A4.pdf") as pdf:
        for li in range(lignes):
            for co in range(colonnes):
                tx0, ty0 = X0 + co * zone_l, Y0 + li * zone_h
                tl, th = min(zone_l, X1 - tx0), min(zone_h, Y1 - ty0)
                fig = plt.figure(figsize=(page_l * CM_EN_POUCES, page_h * CM_EN_POUCES))
                rect = [marge / page_l, 1 - (marge + th) / page_h, tl / page_l, th / page_h]
                ax = nouvel_axe(fig, rect, (tx0, tx0 + tl), (ty0 + th, ty0))
                # Seule la partie utile de la texture, pour garder un PDF léger.
                c0, r0 = int((tx0 - X0) * PX_PAR_CM), int((ty0 - Y0) * PX_PAR_CM)
                c1, r1 = int((tx0 + tl - X0) * PX_PAR_CM), int((ty0 + th - Y0) * PX_PAR_CM)
                ax.imshow(img[r0:r1, c0:c1], extent=(tx0, tx0 + tl, ty0 + th, ty0),
                          interpolation="bilinear", zorder=0)
                dessiner(ax, None)
                ax.add_patch(Rectangle((tx0, ty0), tl, th, fc="none", ec="#888888", lw=0.4, zorder=10))
                fig.text(0.5, 1 - marge / 2 / page_h,
                         f"Ligne {li + 1}/{lignes} · colonne {co + 1}/{colonnes} · "
                         "imprimer à 100 % (taille réelle) · couper sur le cadre, assembler bord à bord",
                         ha="center", va="center", fontsize=7, color="#555555")
                pdf.savefig(fig, dpi=PX_PAR_CM * 2.54)
                plt.close(fig)
    return colonnes, lignes


if __name__ == "__main__":
    img = texture()
    page_a2(img)
    apercu(img)
    colonnes, lignes = tuiles_a4(img)
    print(f"OK : {DOSSIER / 'fond_maquette_A2.pdf'}")
    print(f"OK : {DOSSIER / 'fond_maquette_tuiles_A4.pdf'} ({colonnes} x {lignes} pages A4)")
    print(f"OK : {DOSSIER / 'apercu.png'}")
