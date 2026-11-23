"""Génère le fond imprimable de la maquette (contrat §4.1, iot/maquette.md §2).

Sorties, à l'échelle 1:1 (1 cm sur le plan = 1 cm sur le papier) :
  - fond_maquette_66x46.pdf      : une seule page de 66 x 46 cm (plan 60 x 40 cm + 3 cm de marge),
                                   pour un imprimeur grand format (papier A1 ou rouleau, puis découpe) ;
  - fond_maquette_tuiles_A4.pdf  : la même chose en 8 pages A4 portrait, à assembler bord à bord ;
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
LARGEUR_CM, HAUTEUR_CM = 60, 40
MARGE_CM = 3
PLACES = {  # id: (x, y, largeur, hauteur)
    "A1": (14, 0, 8, 13), "A2": (22, 0, 8, 13), "A3": (30, 0, 8, 13), "A4": (38, 0, 8, 13),
    "B1": (14, 27, 8, 13), "B2": (22, 27, 8, 13), "B3": (30, 27, 8, 13), "B4": (38, 27, 8, 13),
}
ALLEE_Y = (13, 27)
ZONE_INTERDITE = (50, 13, 10, 14)  # allee1
TROTTOIRS = [(0, 0, 14, 13), (46, 0, 14, 13), (0, 27, 14, 13), (46, 27, 14, 13)]

LIGNE_CM = 0.8          # largeur des lignes blanches
PX_PAR_CM = 60          # résolution de la texture (≈ 152 dpi)
CM_EN_POUCES = 1 / 2.54
PT_PAR_CM = 72 / 2.54   # 1 cm en points typographiques

BLANC = "#f4f4f0"
JAUNE = "#f2c218"
BLEU_PMR = "#1f5fbf"
VERT_ELEC = "#1f9a4a"

X0, Y0 = -MARGE_CM, -MARGE_CM
X1, Y1 = LARGEUR_CM + MARGE_CM, HAUTEUR_CM + MARGE_CM


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
    asphalte = 108 + bruit(0, 7) + bruit(1.2, 4) + bruit(40, 4)
    gravillons = rng.random((h, w)) < 0.004
    asphalte[gravillons] += rng.normal(25, 10, gravillons.sum())
    (ax0, ay0), (ax1, ay1) = px(0, 0), px(LARGEUR_CM, HAUTEUR_CM)
    img[ay0:ay1, ax0:ax1] = asphalte[ay0:ay1, ax0:ax1]

    # Trottoirs : béton clair en dalles de 5 cm.
    beton = 172 + bruit(0, 4) + bruit(25, 3)
    for (x, y, l, ht) in TROTTOIRS:
        (tx0, ty0), (tx1, ty1) = px(x, y), px(x + l, y + ht)
        img[ty0:ty1, tx0:tx1] = beton[ty0:ty1, tx0:tx1]
        joint = max(1, int(0.12 * PX_PAR_CM))
        for xx in np.arange(x, x + l + 0.01, 5 if x == 0 else 5):
            cx = px(xx, 0)[0]
            img[ty0:ty1, max(tx0, cx - joint // 2):min(tx1, cx + joint // 2 + 1)] -= 35
        for yy in np.arange(y, y + ht + 0.01, 6.5):
            cy = px(0, yy)[1]
            img[max(ty0, cy - joint // 2):min(ty1, cy + joint // 2 + 1), tx0:tx1] -= 35

    gris = np.clip(img, 0, 255).astype(np.uint8)
    rgb = np.stack([gris, gris, np.clip(gris.astype(np.int16) + 2, 0, 255).astype(np.uint8)], axis=-1)
    return rgb


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
    ax.imshow(img, extent=(X0, X1, Y1, Y0), interpolation="bilinear", zorder=0)

    # Lignes des places : centrées sur les séparations, à l'extérieur sur le fond de rangée.
    for rangee, (y_haut, y_bas, y_fond) in {"A": (0, 13, -LIGNE_CM), "B": (27, 40, 40)}.items():
        for x in (14, 22, 30, 38, 46):
            ax.add_patch(Rectangle((x - LIGNE_CM / 2, y_haut), LIGNE_CM, y_bas - y_haut, fc=BLANC, ec="none"))
        ax.add_patch(Rectangle((14 - LIGNE_CM / 2, y_fond), 32 + LIGNE_CM, LIGNE_CM, fc=BLANC, ec="none"))

    # Zone interdite allee1 : hachures jaunes à 45°, cadre jaune.
    zx, zy, zl, zh = ZONE_INTERDITE
    cadre = box(zx, zy, zx + zl, zy + zh)
    for d in np.arange(-zh, zl + zh, 2.5):
        bande = ShapelyPolygon([(zx + d, zy + zh), (zx + d + 1.0, zy + zh),
                                (zx + d + 1.0 + zh, zy), (zx + d + zh, zy)]).intersection(cadre)
        if not bande.is_empty:
            ax.add_patch(Polygon(list(bande.exterior.coords), fc=JAUNE, ec="none"))
    e = 0.5
    ax.add_patch(Rectangle((zx + e / 2, zy + e / 2), zl - e, zh - e, fc="none", ec=JAUNE, lw=e * PT_PAR_CM))

    # Places spéciales : pictogrammes au fond de la place, loin de l'allée.
    pictogramme_pmr(ax, 18, 3.3, 4)
    pictogramme_electrique(ax, 42, 36.7, 4)

    # Flèches de sens de circulation (entrée à gauche, sortie à droite).
    for x in (2, 26.5):
        ax.add_patch(FancyArrow(x, 20, 5, 0, width=1.0, head_width=2.6, head_length=2.0,
                                length_includes_head=True, fc=BLANC, ec="none"))

    # Repères dans la marge : numéros des places, entrée / sortie.
    police = dict(fontfamily="DejaVu Sans", fontweight="bold", ha="center", va="center", clip_on=True)
    for pid, (x, y, l, h) in PLACES.items():
        ty = -1.9 if pid[0] == "A" else 41.9
        ax.text(x + l / 2, ty, pid, fontsize=fs(1.1), color="#222222", **police)
    ax.text(-1.5, 20, "ENTRÉE", rotation=90, fontsize=fs(1.0), color="#222222", **police)
    ax.text(61.5, 20, "SORTIE", rotation=-90, fontsize=fs(1.0), color="#222222", **police)

    # Repères d'angle (aident la calibration et la pose de la caméra).
    bras, ep = 2.0, 0.25
    for cx, cy, sx, sy in [(0, 0, -1, -1), (60, 0, 1, -1), (0, 40, -1, 1), (60, 40, 1, 1)]:
        ax.add_patch(Rectangle((cx, cy), sx * bras, sy * ep, fc="#111111", ec="none"))
        ax.add_patch(Rectangle((cx, cy), sx * ep, sy * bras, fc="#111111", ec="none"))

    # Barre d'échelle de 10 cm pour vérifier l'impression à la règle.
    bx, by = 1, 41.5
    ax.add_patch(Rectangle((bx, by), 10, 0.3, fc="#111111", ec="none"))
    for i in range(11):
        hauteur = 0.7 if i in (0, 5, 10) else 0.45
        ax.add_patch(Rectangle((bx + i - 0.03, by + 0.3 - hauteur), 0.06, hauteur, fc="#111111", ec="none"))
    ax.text(bx + 5, by + 0.95, "10 cm : vérifier à la règle", fontsize=fs(0.35), color="#111111",
            fontfamily="DejaVu Sans", ha="center", va="center", clip_on=True)
    ax.text(60, 42.6, "Maquette P1 · plan 60 × 40 cm · contrat §4.1 · imprimer à 100 %",
            fontsize=fs(0.3), color="#444444", fontfamily="DejaVu Sans", ha="right", va="center", clip_on=True)


def nouvel_axe(fig, rect, xlim, ylim):
    ax = fig.add_axes(rect)
    ax.set_xlim(*xlim)
    ax.set_ylim(*ylim)
    ax.set_aspect("equal")
    ax.axis("off")
    return ax


def page_unique(img):
    l, h = X1 - X0, Y1 - Y0
    fig = plt.figure(figsize=(l * CM_EN_POUCES, h * CM_EN_POUCES))
    ax = nouvel_axe(fig, [0, 0, 1, 1], (X0, X1), (Y1, Y0))
    dessiner(ax, img)
    fig.savefig(DOSSIER / "fond_maquette_66x46.pdf", dpi=PX_PAR_CM * 2.54)
    fig.savefig(DOSSIER / "apercu.png", dpi=40)
    plt.close(fig)


def tuiles_a4(img):
    page_l, page_h, marge = 21.0, 29.7, 1.0
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
                dessiner_sans_texture(ax)
                ax.add_patch(Rectangle((tx0, ty0), tl, th, fc="none", ec="#888888", lw=0.4, zorder=10))
                fig.text(0.5, 1 - marge / 2 / page_h,
                         f"Ligne {li + 1}/{lignes} · colonne {co + 1}/{colonnes} · "
                         "imprimer à 100 % (taille réelle) · couper sur le cadre, assembler bord à bord",
                         ha="center", va="center", fontsize=7, color="#555555")
                pdf.savefig(fig, dpi=PX_PAR_CM * 2.54)
                plt.close(fig)
    return colonnes, lignes


def dessiner_sans_texture(ax):
    """Même dessin que `dessiner`, sans recharger la texture complète."""
    imshow = ax.imshow
    ax.imshow = lambda *a, **k: None
    try:
        dessiner(ax, None)
    finally:
        ax.imshow = imshow


if __name__ == "__main__":
    img = texture()
    page_unique(img)
    colonnes, lignes = tuiles_a4(img)
    print(f"OK : {DOSSIER / 'fond_maquette_66x46.pdf'}")
    print(f"OK : {DOSSIER / 'fond_maquette_tuiles_A4.pdf'} ({colonnes} x {lignes} pages A4)")
    print(f"OK : {DOSSIER / 'apercu.png'}")
