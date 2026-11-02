"""
Étape 0 — Test de modèles pré-entraînés sur des photos de voitures miniatures vues du dessus.

Objectif : savoir quel modèle reconnaît déjà nos voitures jouets,
ou s'il faudra le réentraîner (fine-tuning) sur des photos de la maquette.

Trois types de modèles testables :
    coco  : YOLO classique (entraîné sur des photos prises au sol)
    obb   : YOLO-OBB (entraîné sur des images aériennes DOTA, rectangles inclinés)
    world : YOLO-World (on décrit en texte ce qu'on cherche)

Utilisation :
    python test_yolo.py --type coco
    python test_yolo.py --type obb
    python test_yolo.py --type world
    python test_yolo.py --type obb --photos photos_test --conf 0.2

Résultats :
    - images annotées dans resultats/<type>/
    - résumé dans le terminal : nombre de voitures détectées par photo
"""

import argparse
import time
from pathlib import Path

from ultralytics import YOLO

MODELES = {
    "coco": {
        "fichier": "yolo11n.pt",
        "classes": [2, 5, 7],  # COCO : car, bus, truck
    },
    "obb": {
        "fichier": "yolo11n-obb.pt",
        "classes": [9, 10],  # DOTA : large vehicle, small vehicle
    },
    "world": {
        "fichier": "yolov8s-world.pt",
        "textes": ["toy car", "car seen from above"],  # ce qu'on cherche, en texte
    },
}

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def main():
    parser = argparse.ArgumentParser(description="Test de modèles pré-entraînés")
    parser.add_argument("--type", choices=MODELES.keys(), default="coco", help="modèle à tester")
    parser.add_argument("--photos", default="photos_test", help="dossier des photos")
    parser.add_argument("--conf", type=float, default=0.25, help="seuil de confiance")
    args = parser.parse_args()

    dossier = Path(args.photos)
    if not dossier.exists():
        print(f"Dossier introuvable : {dossier}")
        return

    images = sorted(p for p in dossier.iterdir() if p.suffix.lower() in EXTENSIONS)
    if not images:
        print(f"Aucune image lisible dans {dossier} (formats acceptés : {', '.join(EXTENSIONS)})")
        return

    config = MODELES[args.type]
    model = YOLO(config["fichier"])  # téléchargé automatiquement la première fois

    options = {}
    if args.type == "world":
        model.set_classes(config["textes"])
    else:
        options["classes"] = config["classes"]

    debut = time.time()
    resultats = model.predict(
        source=[str(p) for p in images],
        conf=args.conf,
        save=True,
        project=str(Path("resultats").resolve()),
        name=args.type,
        exist_ok=True,
        verbose=False,
        **options,
    )
    duree = time.time() - debut

    print(f"\n=== Résumé — modèle {args.type} ({config['fichier']}) ===")
    total = 0
    images_avec_detection = 0
    for r in resultats:
        # Les modèles OBB rangent leurs rectangles dans r.obb, les autres dans r.boxes
        detections = r.obb if args.type == "obb" else r.boxes
        nb = len(detections) if detections is not None else 0
        confs = [f"{c:.2f}" for c in detections.conf.tolist()] if nb else []
        total += nb
        images_avec_detection += 1 if nb else 0
        print(f"{Path(r.path).name:30s} -> {nb} véhicule(s)  confiances: {confs}")

    nb_images = len(resultats)
    print(f"\n{nb_images} image(s) | {images_avec_detection} avec détection | {total} véhicule(s) au total")
    print(f"Temps moyen par image : {duree / nb_images:.2f} s (sur cette machine)")
    print(f"Images annotées : resultats/{args.type}/")


if __name__ == "__main__":
    main()