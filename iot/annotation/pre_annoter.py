"""
Sprint 2 — Pré-annotation automatique avec YOLO-World.

YOLO-World cherche des voitures décrites en texte (« toy car », « car seen from above »).
Chaque détection devient une boîte de la classe 0 (« voiture ») au format YOLO.
Ces boîtes sont un BROUILLON : un humain les corrige ensuite dans Label Studio.

Entrée  : un dossier d'images (photos de test aujourd'hui, photos de la maquette plus tard).
Sortie  : <sortie>/images/       copie des images traitées
          <sortie>/labels/       un .txt par image : "0 x_centre y_centre largeur hauteur" (entre 0 et 1)
          <sortie>/classes.txt   "voiture" (utile pour Label Studio)
          <sortie>/pre_annotations.label-studio.json   (avec --format label-studio)

Utilisation (depuis la racine du dépôt) :
    python iot/annotation/pre_annoter.py --entree iot/photos_test --sortie iot/dataset/pre_annotations/photos --limite 3
    python iot/annotation/pre_annoter.py --entree iot/photos_test --sortie iot/dataset/pre_annotations/photos --format label-studio

Le script n'écrit des fichiers que dans le dossier --sortie.
"""

import argparse
import json
import shutil
import sys
from pathlib import Path

import cv2
from ultralytics import YOLO

from label_studio import RACINE_PAR_DEFAUT, tache  # même dossier que ce script

DOSSIER_IOT = Path(__file__).resolve().parent.parent
MODELE_PAR_DEFAUT = DOSSIER_IOT / "yolov8s-world.pt"
TEXTES = ["toy car", "car seen from above"]   # ce que YOLO-World doit chercher
CLASSE_VOITURE = 0
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def lister_images(dossier):
    """Images du dossier, triées ; renvoie aussi les fichiers ignorés (format non lu)."""
    images, ignorees = [], []
    for p in sorted(dossier.iterdir()):
        if not p.is_file():
            continue
        if p.suffix.lower() in EXTENSIONS and cv2.imread(str(p)) is not None:
            images.append(p)
        else:
            ignorees.append(p.name)
    return images, ignorees


def ligne_yolo(xc, yc, w, h):
    """Une ligne du format YOLO : classe puis centre et taille normalisés (0 à 1)."""
    return f"{CLASSE_VOITURE} {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}"


def main():
    parser = argparse.ArgumentParser(description="Pré-annotation YOLO-World (classe unique 'voiture')")
    parser.add_argument("--entree", required=True, help="dossier des images à annoter")
    parser.add_argument("--sortie", required=True, help="dossier de sortie (images/ + labels/)")
    parser.add_argument("--conf", type=float, default=0.2,
                        help="seuil de confiance (bas exprès : la correction humaine enlève les erreurs)")
    parser.add_argument("--modele", default=str(MODELE_PAR_DEFAUT), help="fichier du modèle YOLO-World")
    parser.add_argument("--format", choices=["yolo", "label-studio"], default="yolo",
                        help="label-studio : produit aussi un fichier d'import pour Label Studio")
    parser.add_argument("--racine-label-studio", default=str(RACINE_PAR_DEFAUT),
                        help="dossier racine des fichiers locaux de Label Studio (défaut : iot/dataset)")
    parser.add_argument("--limite", type=int, default=0, help="ne traiter que les N premières images (test)")
    args = parser.parse_args()

    entree, sortie = Path(args.entree).resolve(), Path(args.sortie).resolve()
    if not entree.is_dir():
        sys.exit(f"Dossier d'entrée introuvable : {entree}")
    if sortie == entree or entree in sortie.parents:
        sys.exit("Le dossier de sortie doit être en dehors du dossier d'entrée.")

    images, ignorees = lister_images(entree)
    if args.limite:
        images = images[: args.limite]
    if not images:
        sys.exit(f"Aucune image lisible dans {entree}")
    noms = [p.stem for p in images]
    doublons = {n for n in noms if noms.count(n) > 1}
    if doublons:
        sys.exit(f"Deux images ont le même nom sans extension : {sorted(doublons)}. Renommez-les.")

    (sortie / "images").mkdir(parents=True, exist_ok=True)
    (sortie / "labels").mkdir(parents=True, exist_ok=True)
    (sortie / "classes.txt").write_text("voiture\n", encoding="utf-8")

    modele = YOLO(args.modele)
    modele.set_classes(TEXTES)

    total_boites, sans_detection, taches = 0, [], []
    for chemin in images:
        r = modele.predict(str(chemin), conf=args.conf, save=False, verbose=False)[0]
        hauteur, largeur = r.orig_shape
        boites = [(*xywhn, conf) for xywhn, conf in zip(r.boxes.xywhn.tolist(), r.boxes.conf.tolist())]

        shutil.copy2(chemin, sortie / "images" / chemin.name)
        lignes = [ligne_yolo(*b[:4]) for b in boites]
        (sortie / "labels" / f"{chemin.stem}.txt").write_text(
            "\n".join(lignes) + ("\n" if lignes else ""), encoding="utf-8")

        total_boites += len(boites)
        if not boites:
            sans_detection.append(chemin.name)
        confs = " ".join(f"{b[4]:.2f}" for b in boites)
        print(f"{chemin.name:30s} {len(boites):3d} voiture(s)  confiances : {confs}")
        if args.format == "label-studio":
            taches.append(tache(sortie / "images" / chemin.name, args.racine_label_studio,
                                boites, largeur, hauteur, "yolov8s-world"))

    if args.format == "label-studio":
        fichier = sortie / "pre_annotations.label-studio.json"
        fichier.write_text(json.dumps(taches, indent=1, ensure_ascii=False), encoding="utf-8")

    print("\n=== Résumé ===")
    print(f"Images traitées      : {len(images)}")
    print(f"Boîtes proposées     : {total_boites} (seuil de confiance {args.conf})")
    print(f"Images sans détection: {len(sans_detection)} -> à vérifier en priorité : {', '.join(sans_detection) or 'aucune'}")
    if ignorees:
        print(f"Fichiers ignorés     : {len(ignorees)} (format non lu, ex. AVIF) : {', '.join(ignorees)}")
    print(f"Sortie               : {sortie}")
    if args.format == "label-studio":
        print(f"Import Label Studio  : {sortie / 'pre_annotations.label-studio.json'}")


if __name__ == "__main__":
    main()
