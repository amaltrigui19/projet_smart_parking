"""Génère les icônes de la PWA (lettre P sur fond teal). Usage : python generer_icones.py (nécessite Pillow)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

DOSSIER = Path(__file__).parent
FOND = (11, 93, 93)
BLANC = (255, 255, 255)
POLICES = ["arialbd.ttf", "DejaVuSans-Bold.ttf", "Arial Bold.ttf"]


def police(taille):
    for nom in POLICES:
        try:
            return ImageFont.truetype(nom, taille)
        except OSError:
            continue
    return ImageFont.load_default(taille)


def icone(taille, marge, arrondi):
    """marge : part du côté laissée vide autour du P (zone de sécurité des icônes maskable)."""
    img = Image.new("RGBA", (taille, taille), (0, 0, 0, 0))
    dessin = ImageDraw.Draw(img)
    dessin.rounded_rectangle((0, 0, taille, taille), radius=int(taille * arrondi), fill=FOND)
    f = police(int(taille * (1 - 2 * marge) * 0.95))
    dessin.text((taille / 2, taille / 2), "P", font=f, fill=BLANC, anchor="mm")
    return img


# Icônes "any" : coins arrondis. Icône maskable : plein cadre, le système applique son masque.
icone(192, 0.18, 0.22).save(DOSSIER / "icon-192.png")
icone(512, 0.18, 0.22).save(DOSSIER / "icon-512.png")
icone(512, 0.28, 0).save(DOSSIER / "icon-maskable-512.png")
print("Icônes générées dans", DOSSIER)
