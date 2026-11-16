"""
Sprint 2 — Vérifier un lot du dataset avant l'entraînement.

Contrôles (une erreur => code de sortie 1) :
    - chaque image a son .txt (un .txt vide est autorisé : aucune voiture sur l'image)
      et chaque .txt a son image ;
    - chaque ligne a 5 valeurs : classe 0, puis x_centre y_centre largeur hauteur entre 0 et 1,
      largeur et hauteur > 0, boîte à l'intérieur de l'image ;
    - chaque image est lisible ; pas deux images identiques (même contenu) ni deux fois le même nom.

Statistiques par source (préfixe du nom : toycar_, carpk_, photos_…) : images, voitures, moyenne,
images vides, taille des boîtes. La taille est mesurée comme si l'image était redimensionnée en
640 × 640 (taille d'entraînement de YOLO) : petite < 32 × 32 px, moyenne < 96 × 96 px, grande au-delà.

Utilisation (depuis la racine du dépôt) :
    python iot/annotation/verifier_dataset.py
    python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot1 --apercu 12
"""

import argparse
import hashlib
import random
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

import cv2

DOSSIER_DATASET = Path(__file__).resolve().parent.parent / "dataset"
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
TAILLE_ENTRAINEMENT = 640
SEUIL_PETITE, SEUIL_MOYENNE = 32 ** 2, 96 ** 2      # aires en pixels (convention COCO)
TOLERANCE = 1e-3                                     # arrondis des coordonnées


def source_de(nom):
    return nom.split("_", 1)[0] if "_" in nom else "(sans préfixe)"


def taille_boite(w, h):
    aire = w * h * TAILLE_ENTRAINEMENT ** 2
    return "petite" if aire < SEUIL_PETITE else "moyenne" if aire < SEUIL_MOYENNE else "grande"


def verifier_label(chemin, erreurs):
    """Renvoie les boîtes valides (xc, yc, w, h) et ajoute les problèmes à `erreurs`."""
    boites = []
    for n, ligne in enumerate(chemin.read_text(encoding="utf-8").splitlines(), start=1):
        v = ligne.split()
        if not v:
            continue
        ou = f"{chemin.name}:{n}"
        if len(v) != 5:
            erreurs.append(f"{ou} : {len(v)} valeurs au lieu de 5")
            continue
        if v[0] != "0":
            erreurs.append(f"{ou} : classe {v[0]} au lieu de 0")
            continue
        try:
            xc, yc, w, h = map(float, v[1:])
        except ValueError:
            erreurs.append(f"{ou} : valeur non numérique")
            continue
        if not all(0 <= c <= 1 for c in (xc, yc, w, h)):
            erreurs.append(f"{ou} : coordonnée hors de [0, 1]")
        elif w <= 0 or h <= 0:
            erreurs.append(f"{ou} : largeur ou hauteur nulle")
        elif xc - w / 2 < -TOLERANCE or xc + w / 2 > 1 + TOLERANCE or yc - h / 2 < -TOLERANCE or yc + h / 2 > 1 + TOLERANCE:
            erreurs.append(f"{ou} : la boîte dépasse du bord de l'image")
        else:
            boites.append((xc, yc, w, h))
    return boites


def dessiner_apercu(images, labels, dossier_apercu, n, graine):
    """Copie N images au hasard avec leurs boîtes dessinées, pour un contrôle visuel."""
    if dossier_apercu.exists():
        shutil.rmtree(dossier_apercu)
    dossier_apercu.mkdir(parents=True)
    for img in random.Random(graine).sample(images, min(n, len(images))):
        im = cv2.imread(str(img))
        h, w = im.shape[:2]
        epaisseur = max(1, round(min(h, w) / 300))
        lab = labels / f"{img.stem}.txt"
        for ligne in lab.read_text(encoding="utf-8").splitlines() if lab.exists() else []:
            v = ligne.split()
            if len(v) != 5:
                continue
            xc, yc, bw, bh = map(float, v[1:])
            cv2.rectangle(im, (int((xc - bw / 2) * w), int((yc - bh / 2) * h)),
                          (int((xc + bw / 2) * w), int((yc + bh / 2) * h)), (0, 0, 255), epaisseur)
        cv2.imwrite(str(dossier_apercu / f"{img.stem}.jpg"), im)


def main():
    parser = argparse.ArgumentParser(description="Vérifie un lot du dataset (format YOLO, classe unique)")
    parser.add_argument("--dossier", default=str(DOSSIER_DATASET / "lot1"), help="dossier avec images/ et labels/")
    parser.add_argument("--apercu", type=int, default=0, help="dessiner les boîtes sur N images au hasard")
    parser.add_argument("--dossier-apercu", default=str(DOSSIER_DATASET / "apercu"),
                        help="où écrire l'aperçu (vidé avant chaque aperçu)")
    parser.add_argument("--graine", type=int, default=0, help="graine du tirage de l'aperçu")
    args = parser.parse_args()

    dossier = Path(args.dossier).resolve()
    images_dir, labels_dir = dossier / "images", dossier / "labels"
    if not images_dir.is_dir() or not labels_dir.is_dir():
        sys.exit(f"Il faut {dossier}/images et {dossier}/labels")

    erreurs = []
    images = sorted(p for p in images_dir.iterdir() if p.suffix.lower() in EXTENSIONS)
    autres = sorted(p.name for p in images_dir.iterdir() if p.suffix.lower() not in EXTENSIONS)
    erreurs += [f"{n} : fichier qui n'est pas une image acceptée" for n in autres]

    # Noms en double (même nom, extensions différentes) et images identiques
    for stem, nb in Counter(p.stem for p in images).items():
        if nb > 1:
            erreurs.append(f"{stem} : {nb} images ont ce nom (un seul .txt possible)")
    empreintes = defaultdict(list)
    for img in images:
        empreintes[hashlib.md5(img.read_bytes()).hexdigest()].append(img.name)
    for noms in empreintes.values():
        if len(noms) > 1:
            erreurs.append(f"images identiques : {', '.join(noms)}")

    # Correspondance images <-> labels
    stems = {p.stem for p in images}
    for lab in sorted(labels_dir.glob("*.txt")):
        if lab.stem not in stems:
            erreurs.append(f"{lab.name} : pas d'image correspondante")

    stats = defaultdict(Counter)
    for img in images:
        src = source_de(img.stem)
        stats[src]["images"] += 1
        if cv2.imread(str(img)) is None:
            erreurs.append(f"{img.name} : image illisible")
        lab = labels_dir / f"{img.stem}.txt"
        if not lab.exists():
            erreurs.append(f"{img.name} : pas de fichier {lab.name}")
            continue
        boites = verifier_label(lab, erreurs)
        stats[src]["voitures"] += len(boites)
        stats[src]["vides"] += 0 if boites else 1
        for _, _, w, h in boites:
            stats[src][taille_boite(w, h)] += 1

    # Statistiques
    print(f"=== Vérification de {dossier} ===\n")
    entete = f"{'source':10s} {'images':>7s} {'voitures':>9s} {'moy./img':>9s} {'vides':>6s} {'petites':>8s} {'moyennes':>9s} {'grandes':>8s}"
    print(entete)
    print("-" * len(entete))
    total = Counter()
    for src in sorted(stats):
        s = stats[src]
        total.update(s)
        moy = s["voitures"] / s["images"] if s["images"] else 0
        print(f"{src:10s} {s['images']:7d} {s['voitures']:9d} {moy:9.1f} {s['vides']:6d} "
              f"{s['petite']:8d} {s['moyenne']:9d} {s['grande']:8d}")
    print("-" * len(entete))
    moy = total["voitures"] / total["images"] if total["images"] else 0
    print(f"{'TOTAL':10s} {total['images']:7d} {total['voitures']:9d} {moy:9.1f} {total['vides']:6d} "
          f"{total['petite']:8d} {total['moyenne']:9d} {total['grande']:8d}")
    print(f"\nTaille des boîtes, image ramenée à {TAILLE_ENTRAINEMENT} × {TAILLE_ENTRAINEMENT} : "
          f"petite < 32 × 32 px, moyenne < 96 × 96 px, grande au-delà.")

    if args.apercu:
        dessiner_apercu(images, labels_dir, Path(args.dossier_apercu).resolve(), args.apercu, args.graine)
        print(f"Aperçu : {min(args.apercu, len(images))} image(s) dans {Path(args.dossier_apercu).resolve()}")

    if erreurs:
        print(f"\n{len(erreurs)} ERREUR(S) :")
        for e in erreurs[:50]:
            print(f"  - {e}")
        if len(erreurs) > 50:
            print(f"  … et {len(erreurs) - 50} autres")
        sys.exit(1)
    print("\nAucune erreur : le lot est prêt.")


if __name__ == "__main__":
    main()
