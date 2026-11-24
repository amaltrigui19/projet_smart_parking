"""Génère les icônes des deux applications (lettre P sur fond teal). Usage : python generate_icons.py (nécessite Pillow)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FOLDER = Path(__file__).parent
BACKGROUND = (11, 93, 93)
WHITE = (255, 255, 255)
FONTS = ["arialbd.ttf", "DejaVuSans-Bold.ttf", "Arial Bold.ttf"]


def font(size):
    for name in FONTS:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def icon(size, margin, radius):
    """margin : part du côté laissée vide autour du P (zone de sécurité des icônes maskable)."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((0, 0, size, size), radius=int(size * radius), fill=BACKGROUND)
    f = font(int(size * (1 - 2 * margin) * 0.95))
    draw.text((size / 2, size / 2), "P", font=f, fill=WHITE, anchor="mm")
    return img


# Icônes "any" : coins arrondis. Icône maskable : plein cadre, le système applique son masque.
icon(192, 0.18, 0.22).save(FOLDER / "icon-192.png")
icon(512, 0.18, 0.22).save(FOLDER / "icon-512.png")
icon(512, 0.28, 0).save(FOLDER / "icon-maskable-512.png")
print("Icônes générées dans", FOLDER)
