"""
Sprint 3 — Archiver un lot du dataset (pour le dossier partagé de l'équipe) et le restaurer.

Un lot (images/ + labels/) n'est jamais dans Git. Pour ne pas le reconstruire à chaque fois et
pour le partager dans l'équipe, on le range dans une archive .zip déposée dans le dossier partagé.

    archiver  : vérifie le lot (verifier_dataset.py), puis crée
                iot/dataset/archives/<lot>_<date>.zip  avec un MANIFEST.json
                (nombre d'images et de voitures par source, empreinte SHA-256 de chaque fichier).
    restaurer : extrait une archive dans iot/dataset/<lot>/, contrôle chaque empreinte,
                puis relance la vérification.

Utilisation (depuis la racine du dépôt) :
    python iot/annotation/archiver_lot.py archiver --lot lot1
    python iot/annotation/archiver_lot.py restaurer --zip "C:/.../lot1_2026-11-16.zip"
    python iot/annotation/archiver_lot.py restaurer --zip lot1_2026-11-16.zip --remplacer
"""

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from collections import Counter
from datetime import datetime
from pathlib import Path

DOSSIER_DATASET = Path(__file__).resolve().parent.parent / "dataset"
VERIFIER = Path(__file__).resolve().parent / "verifier_dataset.py"
SOUS_DOSSIERS = ("images", "labels")


def sha256(chemin):
    h = hashlib.sha256()
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


def verifier(dossier):
    """Lance verifier_dataset.py ; arrête tout si le lot a des erreurs."""
    r = subprocess.run([sys.executable, str(VERIFIER), "--dossier", str(dossier)])
    if r.returncode != 0:
        sys.exit(f"Le lot {dossier.name} a des erreurs (voir ci-dessus) : rien n'est archivé ni restauré.")


def commit_git():
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], capture_output=True,
                              text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def archiver(args):
    dossier = DOSSIER_DATASET / args.lot
    if not all((dossier / s).is_dir() for s in SOUS_DOSSIERS):
        sys.exit(f"Il faut {dossier}/images et {dossier}/labels")
    verifier(dossier)

    fichiers = sorted(p for s in SOUS_DOSSIERS for p in (dossier / s).iterdir() if p.is_file())
    sources = Counter()
    voitures = Counter()
    for p in fichiers:
        source = p.stem.split("_", 1)[0]
        if p.parent.name == "images":
            sources[source] += 1
        else:
            voitures[source] += sum(1 for l in p.read_text(encoding="utf-8").splitlines() if l.strip())

    manifeste = {
        "lot": args.lot,
        "date": datetime.now().isoformat(timespec="seconds"),
        "commit_des_scripts": commit_git(),
        "sources": {s: {"images": sources[s], "voitures": voitures[s]} for s in sorted(sources)},
        "total": {"images": sum(sources.values()), "voitures": sum(voitures.values())},
        "fichiers": {p.relative_to(dossier).as_posix(): sha256(p) for p in fichiers},
    }

    sortie = Path(args.sortie) if args.sortie else (
        DOSSIER_DATASET / "archives" / f"{args.lot}_{datetime.now():%Y-%m-%d}.zip")
    sortie.parent.mkdir(parents=True, exist_ok=True)
    # Les images sont déjà compressées (JPEG) : ZIP_STORED est aussi petit et bien plus rapide
    with zipfile.ZipFile(sortie, "w", zipfile.ZIP_STORED) as z:
        z.writestr(f"{args.lot}/MANIFEST.json", json.dumps(manifeste, indent=1, ensure_ascii=False))
        for p in fichiers:
            z.write(p, f"{args.lot}/{p.relative_to(dossier).as_posix()}")

    print(f"\nArchive : {sortie} ({sortie.stat().st_size // 1024} Ko)")
    for s, n in manifeste["sources"].items():
        print(f"  {s:10s} {n['images']:5d} images {n['voitures']:6d} voitures")
    print(f"  {'TOTAL':10s} {manifeste['total']['images']:5d} images {manifeste['total']['voitures']:6d} voitures")
    print(f"SHA-256 de l'archive : {sha256(sortie)}")
    print("À déposer dans le dossier partagé (Drive), avec une ligne dans son LISEZMOI.")


def restaurer(args):
    archive = Path(args.zip)
    if not archive.is_file():
        sys.exit(f"Archive introuvable : {archive}")
    with zipfile.ZipFile(archive) as z:
        noms = z.namelist()
        lots = {n.split("/", 1)[0] for n in noms}
        if len(lots) != 1:
            sys.exit(f"L'archive doit contenir un seul dossier de lot, trouvé : {sorted(lots)}")
        lot = lots.pop()
        if any(n.startswith("/") or ".." in Path(n).parts for n in noms):
            sys.exit("Archive refusée : chemin suspect à l'intérieur")
        manifeste = json.loads(z.read(f"{lot}/MANIFEST.json"))

        cible = DOSSIER_DATASET / lot
        if cible.exists() and any(cible.iterdir()):
            if not args.remplacer:
                sys.exit(f"{cible} existe déjà : ajouter --remplacer pour l'écraser")
            shutil.rmtree(cible)
        z.extractall(DOSSIER_DATASET)

    cible = DOSSIER_DATASET / lot
    abimes = [nom for nom, empreinte in manifeste["fichiers"].items()
              if not (cible / nom).is_file() or sha256(cible / nom) != empreinte]
    if abimes:
        sys.exit(f"{len(abimes)} fichier(s) manquant(s) ou abîmé(s), ex. : {abimes[:5]}")
    print(f"Restauré : {cible} ({manifeste['total']['images']} images, {manifeste['total']['voitures']} voitures, "
          f"archivé le {manifeste['date']}, scripts {manifeste.get('commit_des_scripts')})\n", flush=True)
    verifier(cible)


def main():
    parser = argparse.ArgumentParser(description="Archiver / restaurer un lot du dataset")
    sous = parser.add_subparsers(dest="commande", required=True)

    a = sous.add_parser("archiver", help="lot vérifié -> archive .zip avec manifeste")
    a.add_argument("--lot", required=True, help="nom du dossier dans iot/dataset (ex. lot1)")
    a.add_argument("--sortie", help="fichier .zip produit (défaut : iot/dataset/archives/<lot>_<date>.zip)")
    a.set_defaults(fonction=archiver)

    r = sous.add_parser("restaurer", help="archive .zip -> iot/dataset/<lot>, avec contrôle")
    r.add_argument("--zip", required=True, help="archive téléchargée depuis le dossier partagé")
    r.add_argument("--remplacer", action="store_true", help="écraser le lot s'il existe déjà")
    r.set_defaults(fonction=restaurer)

    args = parser.parse_args()
    args.fonction(args)


if __name__ == "__main__":
    main()
