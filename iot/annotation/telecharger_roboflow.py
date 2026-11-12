"""
Sprint 2 — Télécharger une version d'un dataset Roboflow au format YOLO, avec SA clé API.

La clé n'est jamais écrite dans le code : elle est lue dans la variable d'environnement
ROBOFLOW_API_KEY, ou à défaut dans le fichier iot/.env (ignoré par Git) :
    ROBOFLOW_API_KEY=votre_cle

Utilisation (depuis la racine du dépôt) :
    # Voir les classes, la licence et les versions d'un projet (sans rien télécharger)
    python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno --info
    # Lister les projets de son propre workspace (utile après un fork)
    python iot/annotation/telecharger_roboflow.py --mes-projets
    # Télécharger une version au format YOLOv8 dans iot/dataset/sources/<nom>
    python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno \\
        --version 4 --sortie iot/dataset/sources/toycar_v4

La clé n'est jamais affichée, même en cas d'erreur.
"""

import argparse
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.roboflow.com"
FICHIER_ENV = Path(__file__).resolve().parent.parent / ".env"


def lire_cle():
    cle = os.environ.get("ROBOFLOW_API_KEY", "").strip()
    if not cle and FICHIER_ENV.exists():
        for ligne in FICHIER_ENV.read_text(encoding="utf-8").splitlines():
            if ligne.strip().startswith("ROBOFLOW_API_KEY="):
                cle = ligne.split("=", 1)[1].strip()
    if not cle:
        sys.exit("Clé API absente : définir ROBOFLOW_API_KEY ou l'écrire dans iot/.env (voir iot/.env.exemple)")
    return cle


def appeler(chemin, cle):
    """GET sur l'API Roboflow ; renvoie le JSON. Les messages d'erreur ne contiennent pas la clé."""
    url = f"{API}/{chemin}?api_key={cle}"
    try:
        with urllib.request.urlopen(url, timeout=120) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit(f"Erreur Roboflow {e.code} sur /{chemin} (clé invalide, projet privé ou nom incorrect ?)")
    except urllib.error.URLError as e:
        sys.exit(f"Connexion impossible à Roboflow : {e.reason}")


def afficher_projet(projet, cle):
    d = appeler(projet, cle)
    p = d.get("project", {})
    print(f"Projet  : {projet} ({p.get('name')})")
    print(f"Licence : {p.get('license')}")
    print(f"Images  : {p.get('images')} | classes (nombre de boîtes) : {p.get('classes')}")
    versions = d.get("versions", [])
    if not versions:
        print("Aucune version générée : impossible d'exporter (faire un fork puis générer une version).")
    for v in versions:
        print(f"  version {v.get('id', '').split('/')[-1]} : {v.get('images')} images, "
              f"répartition {v.get('splits')}")


def main():
    parser = argparse.ArgumentParser(description="Téléchargement d'un dataset Roboflow (format YOLO)")
    parser.add_argument("--projet", help="espace/projet, ex. parkitnowprototype/toy-car-detection-f5tno")
    parser.add_argument("--version", type=int, help="numéro de version à télécharger")
    parser.add_argument("--format", default="yolov8", help="format d'export (défaut : yolov8)")
    parser.add_argument("--sortie", help="dossier où extraire le dataset (doit être vide ou absent)")
    parser.add_argument("--info", action="store_true", help="afficher les infos du projet, sans télécharger")
    parser.add_argument("--mes-projets", action="store_true", help="lister les projets de mon workspace")
    args = parser.parse_args()
    cle = lire_cle()

    if args.mes_projets:
        espace = appeler("", cle)["workspace"]
        print(f"Workspace : {espace}")
        for p in appeler(espace, cle)["workspace"].get("projects", []):
            print(f"  {p.get('id')} | {p.get('name')} | {p.get('images')} images | "
                  f"{p.get('versions')} version(s) | classes {p.get('classes')}")
        return
    if not args.projet:
        sys.exit("Indiquer --projet (ou --mes-projets)")
    if args.info or not args.version:
        afficher_projet(args.projet, cle)
        return
    if not args.sortie:
        sys.exit("Indiquer --sortie pour télécharger")

    sortie = Path(args.sortie)
    if sortie.exists() and any(sortie.iterdir()):
        sys.exit(f"{sortie} n'est pas vide : le supprimer ou choisir un autre dossier")

    # Roboflow prépare l'export en arrière-plan : on redemande jusqu'à obtenir le lien (5 min max)
    chemin = f"{args.projet}/{args.version}/{args.format}"
    for _ in range(30):
        d = appeler(chemin, cle)
        lien = d.get("export", {}).get("link")
        if lien:
            break
        print("Export en préparation chez Roboflow…")
        time.sleep(10)
    else:
        sys.exit("L'export n'est toujours pas prêt : réessayer dans quelques minutes")

    with urllib.request.urlopen(lien, timeout=900) as r:
        donnees = r.read()
    sortie.mkdir(parents=True, exist_ok=True)
    zipfile.ZipFile(io.BytesIO(donnees)).extractall(sortie)

    print(f"Téléchargé : {len(donnees) // 1024} Ko -> {sortie}")
    for split in ("train", "valid", "test"):
        if (sortie / split / "images").is_dir():
            print(f"  {split:5s} : {len(list((sortie / split / 'images').iterdir()))} images")
    if (sortie / "data.yaml").exists():
        print((sortie / "data.yaml").read_text(encoding="utf-8").split("roboflow:")[0].strip())


if __name__ == "__main__":
    main()
