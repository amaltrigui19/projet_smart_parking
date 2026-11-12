"""
Sprint 2 — Préparer un dataset YOLO pour le lot : une seule classe « voiture » (id 0).

Accepte deux formes d'entrée :
    - un export Roboflow « YOLOv8 / YOLO11 » : data.yaml + train/ valid/ test/ (images/ + labels/)
    - un dossier simple : images/ + labels/ + classes.txt (export YOLO de Label Studio, sortie de pre_annoter.py)

Ce que fait le script :
    1. garde seulement les classes demandées (--classes-a-garder) et les renumérote en 0 ;
    2. convertit les polygones (annotations de segmentation) en rectangles englobants ;
    3. fusionne les boîtes en double sur la même voiture (recouvrement > --seuil-doublons) ;
    4. option : une seule variante par image d'origine (exports Roboflow augmentés, noms "xxx.rf.<hash>") ;
    5. option : ignore les images sans voiture et celles listées dans --exclusions ;
       option : remplace les labels par les corrections manuelles de --corrections (fichiers .txt versionnés) ;
    6. option : tire au hasard N images (--echantillon), avec une graine fixe (reproductible) ;
    7. copie le résultat dans <sortie>/images et <sortie>/labels, préfixé par --nom (ex. toycar_...).

Relancer le script remplace les fichiers de la même source (même préfixe), sans toucher aux autres.

Exemples (depuis la racine du dépôt) :
    python iot/annotation/preparer_datasets.py --source iot/dataset/sources/toycar_v4 --nom toycar \\
        --classes-a-garder toycar --une-variante-par-origine --ignorer-vides \\
        --exclusions iot/annotation/exclusions_toycar.txt --corrections iot/annotation/corrections
    python iot/annotation/preparer_datasets.py --source iot/dataset/sources/carpk_v1 --nom carpk --echantillon 80
    python iot/annotation/preparer_datasets.py --source iot/dataset/corrections/photos --nom photos
"""

import argparse
import random
import re
import shutil
import sys
from collections import Counter
from pathlib import Path

import yaml

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
SORTIE_PAR_DEFAUT = Path(__file__).resolve().parent.parent / "dataset" / "lot1"
SUFFIXE_ROBOFLOW = re.compile(r"\.rf\.[0-9a-f]+$")   # "photo_jpg.rf.3fc7..." -> variante augmentée


def noms_des_classes(source):
    """Noms des classes, dans l'ordre des identifiants (data.yaml ou classes.txt)."""
    if (source / "data.yaml").exists():
        noms = yaml.safe_load((source / "data.yaml").read_text(encoding="utf-8"))["names"]
        return list(noms.values()) if isinstance(noms, dict) else list(noms)
    if (source / "classes.txt").exists():
        return [l.strip() for l in (source / "classes.txt").read_text(encoding="utf-8").splitlines() if l.strip()]
    sys.exit(f"Ni data.yaml ni classes.txt dans {source}")


def lister_paires(source):
    """(image, fichier .txt) de tous les splits (train/valid/test) ou du dossier simple."""
    dossiers = [d for d in (source / s for s in ("train", "valid", "test")) if d.is_dir()] or [source]
    paires = []
    for d in dossiers:
        for img in sorted((d / "images").iterdir()):
            if img.suffix.lower() in EXTENSIONS:
                paires.append((img, d / "labels" / f"{img.stem}.txt"))
    return paires


def ligne_en_boite(valeurs):
    """Une ligne YOLO -> (x1, y1, x2, y2) entre 0 et 1. Accepte rectangle (4 nombres) ou polygone (2n nombres)."""
    v = [float(x) for x in valeurs]
    if len(v) == 4:
        xc, yc, w, h = v
        x1, y1, x2, y2 = xc - w / 2, yc - h / 2, xc + w / 2, yc + h / 2
    elif len(v) >= 6 and len(v) % 2 == 0:
        xs, ys = v[0::2], v[1::2]                       # polygone -> rectangle englobant
        x1, y1, x2, y2 = min(xs), min(ys), max(xs), max(ys)
    else:
        return None
    x1, y1, x2, y2 = (min(max(c, 0.0), 1.0) for c in (x1, y1, x2, y2))
    return (x1, y1, x2, y2) if x2 > x1 and y2 > y1 else None


def iou(a, b):
    """Recouvrement de deux boîtes : aire de l'intersection / aire de l'union."""
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union else 0.0


def lire_boites(label, ids_gardes, seuil_doublons, stats):
    """Boîtes « voiture » d'une image, sans les doublons."""
    boites = []
    if not label.exists():
        return boites
    for ligne in label.read_text(encoding="utf-8").splitlines():
        v = ligne.split()
        if not v:
            continue
        if int(v[0]) not in ids_gardes:
            stats["boites_classe_retiree"] += 1
            continue
        boite = ligne_en_boite(v[1:])
        if boite is None:
            stats["boites_invalides"] += 1
            continue
        if len(v) > 5:
            stats["polygones_convertis"] += 1
        if seuil_doublons and any(iou(boite, b) > seuil_doublons for b in boites):
            stats["doublons_fusionnes"] += 1
            continue
        boites.append(boite)
    return boites


def ligne_yolo(b):
    x1, y1, x2, y2 = b
    return f"0 {(x1 + x2) / 2:.6f} {(y1 + y2) / 2:.6f} {x2 - x1:.6f} {y2 - y1:.6f}"


def main():
    parser = argparse.ArgumentParser(description="Convertit un dataset YOLO vers la classe unique 'voiture'")
    parser.add_argument("--source", required=True, help="dossier du dataset (export Roboflow ou images/+labels/)")
    parser.add_argument("--nom", required=True, help="préfixe des fichiers produits : toycar, carpk, photos…")
    parser.add_argument("--sortie", default=str(SORTIE_PAR_DEFAUT), help="dossier du lot (défaut : iot/dataset/lot1)")
    parser.add_argument("--classes-a-garder", nargs="*", help="noms des classes qui sont des voitures (défaut : toutes)")
    parser.add_argument("--echantillon", type=int, default=0, help="ne garder que N images tirées au hasard")
    parser.add_argument("--graine", type=int, default=42, help="graine du tirage (même graine = même échantillon)")
    parser.add_argument("--une-variante-par-origine", action="store_true",
                        help="export augmenté : une seule image par image d'origine")
    parser.add_argument("--ignorer-vides", action="store_true", help="ignorer les images sans aucune voiture")
    parser.add_argument("--exclusions", help="fichier texte : une image d'origine à exclure par ligne")
    parser.add_argument("--corrections",
                        help="dossier de .txt corrigés à la main (même nom que l'image produite) : remplacent les labels")
    parser.add_argument("--seuil-doublons", type=float, default=0.9,
                        help="fusionne deux boîtes qui se recouvrent plus que ce seuil (0 = désactivé)")
    args = parser.parse_args()

    source, sortie = Path(args.source).resolve(), Path(args.sortie).resolve()
    if not re.fullmatch(r"[a-z0-9]+", args.nom):
        sys.exit("--nom : lettres minuscules et chiffres seulement (ex. toycar)")

    classes = noms_des_classes(source)
    gardees = args.classes_a_garder or classes
    inconnues = set(gardees) - set(classes)
    if inconnues:
        sys.exit(f"Classes inconnues : {sorted(inconnues)}. Classes du dataset : {classes}")
    ids_gardes = {i for i, n in enumerate(classes) if n in gardees}

    exclues = set()
    if args.exclusions:
        for l in Path(args.exclusions).read_text(encoding="utf-8").splitlines():
            nom = l.split("#")[0].strip()
            if nom:
                exclues.add(nom)

    stats = Counter()
    paires = lister_paires(source)
    stats["images_source"] = len(paires)

    # 1. une variante par image d'origine
    choisies, vues = [], set()
    for img, lab in paires:
        origine = SUFFIXE_ROBOFLOW.sub("", img.stem)
        if args.une_variante_par_origine:
            if origine in vues:
                stats["variantes_ecartees"] += 1
                continue
            vues.add(origine)
        choisies.append((img, lab, origine))

    # 2. exclusions, lecture des boîtes, images vides
    retenues = []
    for img, lab, origine in choisies:
        if origine in exclues:
            stats["exclues_par_liste"] += 1
            continue
        boites = lire_boites(lab, ids_gardes, args.seuil_doublons, stats)
        if args.ignorer_vides and not boites:
            stats["vides_ignorees"] += 1
            continue
        retenues.append((img, origine, boites))

    # 3. échantillon reproductible : on trie par nom d'origine (sans le code ".rf.…" ni le split,
    #    qui changent d'un export ou d'un fork à l'autre) avant le tirage avec la graine
    retenues.sort(key=lambda r: r[1])
    if args.echantillon and args.echantillon < len(retenues):
        retenues = sorted(random.Random(args.graine).sample(retenues, args.echantillon), key=lambda r: r[1])

    # 4. écriture : on remplace les fichiers de cette source seulement
    for sous in ("images", "labels"):
        (sortie / sous).mkdir(parents=True, exist_ok=True)
        for ancien in (sortie / sous).glob(f"{args.nom}_*"):
            ancien.unlink()
    origines_uniques = len({o for _, o, _ in retenues}) == len(retenues)
    dossier_corr = Path(args.corrections) if args.corrections else None
    total, corrigees = 0, []
    for img, origine, boites in retenues:
        base = f"{args.nom}_{origine if origines_uniques else img.stem}"
        shutil.copy2(img, sortie / "images" / f"{base}{img.suffix.lower()}")
        lignes = [ligne_yolo(b) for b in boites]
        if dossier_corr and (dossier_corr / f"{base}.txt").exists():
            lignes = [l for l in (dossier_corr / f"{base}.txt").read_text(encoding="utf-8").splitlines() if l.strip()]
            corrigees.append(base)
        (sortie / "labels" / f"{base}.txt").write_text("\n".join(lignes) + ("\n" if lignes else ""), encoding="utf-8")
        total += len(lignes)

    print(f"=== {args.nom} : {source.name} -> {sortie} ===")
    print(f"Classes du dataset : {classes} | gardées comme 'voiture' : {sorted(gardees)}")
    print(f"Images dans la source       : {stats['images_source']}")
    for cle, texte in [("variantes_ecartees", "Variantes augmentées écartées"),
                       ("exclues_par_liste", "Exclues par la liste"),
                       ("vides_ignorees", "Images sans voiture ignorées")]:
        if stats[cle]:
            print(f"{texte:28s}: {stats[cle]}")
    if args.echantillon:
        print(f"Échantillon                 : {args.echantillon} (graine {args.graine})")
    print(f"Images écrites              : {len(retenues)}")
    print(f"Voitures (boîtes) écrites   : {total}")
    if args.corrections:
        print(f"Corrections manuelles       : {len(corrigees)} ({', '.join(corrigees) or 'aucune'})")
    for cle, texte in [("polygones_convertis", "Polygones -> rectangles"),
                       ("doublons_fusionnes", "Boîtes en double fusionnées"),
                       ("boites_classe_retiree", "Boîtes d'une classe retirée"),
                       ("boites_invalides", "Boîtes invalides ignorées")]:
        if stats[cle]:
            print(f"{texte:28s}: {stats[cle]}")


if __name__ == "__main__":
    main()
