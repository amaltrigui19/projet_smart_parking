"""
Sprint 3 — Découper le dataset complet (lots 1, 2, 3…) en entraînement / validation / test.

Chaque image est placée d'après une empreinte (hachage) de son NOM, et non par un tirage au hasard :
    - le découpage est identique sur tous les PC, sans graine à partager ;
    - ajouter des images plus tard (photos réelles de la maquette) ne déplace AUCUNE image déjà placée :
      une image de test reste en test, le modèle ne la verra jamais à l'entraînement ;
    - chaque source (préfixe toycar_, carpk_, synth_…) est répartie dans les mêmes proportions.

Les variantes d'une même photo ne doivent pas être séparées (sinon le test contient presque une image
d'entraînement) : le lot 1 ne garde déjà qu'une variante par photo d'origine (--une-variante-par-origine),
et les images synthétiques sont toutes indépendantes.

Sortie (dossiers recréés à chaque lancement, les lots ne sont jamais modifiés) :
    iot/dataset/entrainement/images + labels
    iot/dataset/validation/images + labels
    iot/dataset/test/images + labels
    iot/dataset/decoupage.csv     (image, lot, source, partie)

Utilisation (depuis la racine du dépôt) :
    python iot/annotation/decouper_dataset.py
    python iot/annotation/decouper_dataset.py --lots lot1 lot2 lot3 --validation 0.15 --test 0.15
"""

import argparse
import csv
import hashlib
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

DOSSIER_DATASET = Path(__file__).resolve().parent.parent / "dataset"
EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
PARTIES = ("entrainement", "validation", "test")


def source_de(nom):
    """Préfixe du nom de fichier : toycar, carpk, photos, synth, maquette…"""
    return nom.split("_", 1)[0] if "_" in nom else "(sans préfixe)"


def partie_de(nom, part_validation, part_test):
    """Nombre stable entre 0 et 1 tiré du nom (SHA-256), puis seuils : test, validation, entraînement."""
    u = int(hashlib.sha256(nom.encode("utf-8")).hexdigest()[:12], 16) / 16 ** 12
    if u < part_test:
        return "test"
    if u < part_test + part_validation:
        return "validation"
    return "entrainement"


def main():
    parser = argparse.ArgumentParser(description="Découpe les lots en entraînement / validation / test")
    parser.add_argument("--dataset", default=str(DOSSIER_DATASET), help="dossier iot/dataset")
    parser.add_argument("--lots", nargs="+", default=["lot1", "lot2", "lot3"], help="lots à réunir")
    parser.add_argument("--validation", type=float, default=0.15, help="part de la validation (défaut 0,15)")
    parser.add_argument("--test", type=float, default=0.15, help="part du test (défaut 0,15)")
    args = parser.parse_args()

    if not (0 < args.validation < 1 and 0 < args.test < 1 and args.validation + args.test < 1):
        sys.exit("--validation et --test doivent être entre 0 et 1, et leur somme < 1")

    dataset = Path(args.dataset)
    images = []                                            # (chemin image, chemin label, lot)
    for lot in args.lots:
        dossier = dataset / lot
        if not (dossier / "images").is_dir():
            sys.exit(f"Lot introuvable : {dossier}/images (le télécharger depuis le Drive ou le générer)")
        for img in sorted((dossier / "images").iterdir()):
            if img.suffix.lower() in EXTENSIONS:
                images.append((img, dossier / "labels" / f"{img.stem}.txt", lot))

    noms = Counter(img.stem for img, _, _ in images)
    doublons = [n for n, c in noms.items() if c > 1]
    if doublons:
        sys.exit(f"Même nom dans deux lots (les préfixes doivent être uniques) : {doublons[:5]}")

    for partie in PARTIES:                                 # relancer remplace le découpage
        if (dataset / partie).exists():
            shutil.rmtree(dataset / partie)
        for sous in ("images", "labels"):
            (dataset / partie / sous).mkdir(parents=True)

    stats = defaultdict(Counter)                           # source -> {partie: nombre d'images}
    voitures = Counter()                                   # partie -> nombre de boîtes
    with open(dataset / "decoupage.csv", "w", newline="", encoding="utf-8") as f:
        journal = csv.writer(f)
        journal.writerow(["image", "lot", "source", "partie"])
        for img, label, lot in images:
            partie = partie_de(img.stem, args.validation, args.test)
            shutil.copy2(img, dataset / partie / "images" / img.name)
            if label.exists():
                shutil.copy2(label, dataset / partie / "labels" / label.name)
                voitures[partie] += sum(1 for l in label.read_text(encoding="utf-8").splitlines() if l.strip())
            else:                                          # image sans voiture : label vide
                (dataset / partie / "labels" / f"{img.stem}.txt").write_text("", encoding="utf-8")
            source = source_de(img.stem)
            stats[source][partie] += 1
            journal.writerow([img.name, lot, source, partie])

    print(f"{'source':<12}{'entrainement':>14}{'validation':>12}{'test':>8}{'total':>8}")
    print("-" * 54)
    totaux = Counter()
    for source in sorted(stats):
        c = stats[source]
        totaux.update(c)
        print(f"{source:<12}{c['entrainement']:>14}{c['validation']:>12}{c['test']:>8}{sum(c.values()):>8}")
    print("-" * 54)
    print(f"{'TOTAL':<12}{totaux['entrainement']:>14}{totaux['validation']:>12}{totaux['test']:>8}{sum(totaux.values()):>8}")
    print(f"{'voitures':<12}{voitures['entrainement']:>14}{voitures['validation']:>12}{voitures['test']:>8}{sum(voitures.values()):>8}")
    print(f"\nListe complète : {dataset / 'decoupage.csv'}")


if __name__ == "__main__":
    main()
