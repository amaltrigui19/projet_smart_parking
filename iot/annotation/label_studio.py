"""
Sprint 2 — Passerelle entre nos fichiers YOLO et Label Studio (correction manuelle des boîtes).

Label Studio (en local) affiche les images de iot/dataset/ grâce au « service de fichiers locaux » :
une image est désignée par l'adresse  /data/local-files/?d=<chemin relatif à iot/dataset>.

Deux commandes :
    vers-ls   : labels YOLO existants  ->  fichier d'import Label Studio (boîtes proposées à corriger)
    depuis-ls : export « JSON » de Label Studio  ->  dossier YOLO (images/ + labels/ + classes.txt)

Exemples (depuis la racine du dépôt) :
    # Corriger une image de toy-car déjà dans le lot 1
    python iot/annotation/label_studio.py vers-ls --dossier iot/dataset/lot1 --fichiers toycar_toy-car-21-Copy_jpeg
    # Récupérer les photos corrigées (export JSON de Label Studio)
    python iot/annotation/label_studio.py depuis-ls --export export.json --sortie iot/dataset/corrections/photos
    # Récupérer seulement les labels corrigés, sans recopier les images (correction dans le lot)
    python iot/annotation/label_studio.py depuis-ls --export export.json --sortie iot/dataset/lot1 --labels-seulement
"""

import argparse
import json
import shutil
import sys
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlparse

RACINE_PAR_DEFAUT = Path(__file__).resolve().parent.parent / "dataset"
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
PREFIXE = "/data/local-files/?d="


def url_label_studio(image, racine):
    """Adresse d'une image pour Label Studio : chemin relatif à la racine, avec des '/'."""
    try:
        relatif = Path(image).resolve().relative_to(Path(racine).resolve())
    except ValueError:
        sys.exit(f"{image} n'est pas dans la racine Label Studio {racine} "
                 f"(LABEL_STUDIO_LOCAL_FILES_DOCUMENT_ROOT)")
    return PREFIXE + quote(relatif.as_posix())


def chemin_depuis_url(url, racine):
    """Inverse de url_label_studio : retrouve le fichier image sur le disque."""
    d = parse_qs(urlparse(url).query).get("d")
    if not d:
        return None
    return Path(racine) / unquote(d[0])


def resultat_boite(i, xc, yc, w, h, largeur, hauteur, score=None):
    """Une boîte au format Label Studio (pourcentages de l'image, coin haut-gauche)."""
    r = {
        "id": f"b{i}", "type": "rectanglelabels", "from_name": "label", "to_name": "image",
        "original_width": largeur, "original_height": hauteur, "image_rotation": 0,
        "value": {"x": (xc - w / 2) * 100, "y": (yc - h / 2) * 100, "width": w * 100, "height": h * 100,
                  "rotation": 0, "rectanglelabels": ["voiture"]},
    }
    if score is not None:
        r["score"] = round(score, 3)
    return r


def tache(image, racine, boites, largeur, hauteur, version):
    """Une tâche Label Studio : l'image + les boîtes proposées. boites = [(xc, yc, w, h, score|None)]."""
    return {
        "data": {"image": url_label_studio(image, racine)},
        "predictions": [{"model_version": version,
                         "result": [resultat_boite(i, *b[:4], largeur, hauteur, b[4]) for i, b in enumerate(boites)]}],
    }


def vers_ls(args):
    import cv2
    dossier = Path(args.dossier)
    images = sorted(p for p in (dossier / "images").iterdir() if p.suffix.lower() in EXTENSIONS)
    if args.fichiers:
        voulus = {Path(f).stem for f in args.fichiers}
        images = [p for p in images if p.stem in voulus]
        manquants = voulus - {p.stem for p in images}
        if manquants:
            sys.exit(f"Introuvables dans {dossier / 'images'} : {sorted(manquants)}")
    taches = []
    for img in images:
        h, w = cv2.imread(str(img)).shape[:2]
        lab = dossier / "labels" / f"{img.stem}.txt"
        boites = []
        for ligne in lab.read_text(encoding="utf-8").splitlines() if lab.exists() else []:
            v = ligne.split()
            if len(v) == 5:
                boites.append((*map(float, v[1:]), None))
        taches.append(tache(img, args.racine, boites, w, h, "labels-existants"))
    sortie = Path(args.sortie or dossier / "a_corriger.label-studio.json")
    sortie.write_text(json.dumps(taches, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"{len(taches)} tâche(s) écrite(s) dans {sortie}")


def depuis_ls(args):
    taches = json.loads(Path(args.export).read_text(encoding="utf-8"))
    sortie = Path(args.sortie)
    (sortie / "labels").mkdir(parents=True, exist_ok=True)
    if not args.labels_seulement:
        (sortie / "images").mkdir(parents=True, exist_ok=True)
        (sortie / "classes.txt").write_text("voiture\n", encoding="utf-8")

    ecrites, boites_total, non_annotees, rotations = 0, 0, [], 0
    for t in taches:
        image = chemin_depuis_url(t["data"]["image"], args.racine)
        if image is None:
            sys.exit(f"Adresse d'image non reconnue (fichier local attendu) : {t['data']['image']}")
        annotations = [a for a in t.get("annotations", []) if not a.get("was_cancelled")]
        if not annotations:
            non_annotees.append(image.name)     # jamais validée par un humain : on ne la garde pas
            continue
        derniere = max(annotations, key=lambda a: a.get("updated_at") or a.get("created_at") or "")
        lignes = []
        for r in derniere.get("result", []):
            if r.get("type") != "rectanglelabels":
                continue
            v = r["value"]
            rotations += 1 if abs(v.get("rotation", 0)) > 0.5 else 0
            x, y, w, h = v["x"] / 100, v["y"] / 100, v["width"] / 100, v["height"] / 100
            x1, y1 = max(0.0, x), max(0.0, y)
            x2, y2 = min(1.0, x + w), min(1.0, y + h)
            if x2 > x1 and y2 > y1:
                lignes.append(f"0 {(x1 + x2) / 2:.6f} {(y1 + y2) / 2:.6f} {x2 - x1:.6f} {y2 - y1:.6f}")
        (sortie / "labels" / f"{image.stem}.txt").write_text(
            "\n".join(lignes) + ("\n" if lignes else ""), encoding="utf-8")
        if not args.labels_seulement:
            if not image.exists():
                sys.exit(f"Image introuvable sur le disque : {image}")
            if image.resolve() != (sortie / "images" / image.name).resolve():
                shutil.copy2(image, sortie / "images" / image.name)
        ecrites += 1
        boites_total += len(lignes)

    print(f"Images corrigées écrites : {ecrites} | voitures : {boites_total} -> {sortie}")
    if non_annotees:
        print(f"Tâches sans annotation validée (ignorées, à finir dans Label Studio) : {len(non_annotees)} : "
              f"{', '.join(non_annotees)}")
    if rotations:
        print(f"Attention : {rotations} boîte(s) tournée(s) dans Label Studio ; la rotation est ignorée en YOLO.")


def main():
    parser = argparse.ArgumentParser(description="Passerelle YOLO <-> Label Studio")
    sous = parser.add_subparsers(dest="commande", required=True)

    p1 = sous.add_parser("vers-ls", help="labels YOLO -> fichier d'import Label Studio")
    p1.add_argument("--dossier", required=True, help="dossier avec images/ et labels/")
    p1.add_argument("--fichiers", nargs="*", help="seulement ces images (nom avec ou sans extension)")
    p1.add_argument("--sortie", help="fichier JSON produit (défaut : <dossier>/a_corriger.label-studio.json)")
    p1.add_argument("--racine", default=str(RACINE_PAR_DEFAUT), help="racine des fichiers locaux de Label Studio")
    p1.set_defaults(fonction=vers_ls)

    p2 = sous.add_parser("depuis-ls", help="export JSON de Label Studio -> dossier YOLO")
    p2.add_argument("--export", required=True, help="fichier exporté depuis Label Studio (format JSON)")
    p2.add_argument("--sortie", required=True, help="dossier YOLO de sortie")
    p2.add_argument("--labels-seulement", action="store_true", help="n'écrire que les .txt (images déjà en place)")
    p2.add_argument("--racine", default=str(RACINE_PAR_DEFAUT), help="racine des fichiers locaux de Label Studio")
    p2.set_defaults(fonction=depuis_ls)

    args = parser.parse_args()
    args.fonction(args)


if __name__ == "__main__":
    main()
