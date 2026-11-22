"""
Sprint 4 — Évaluer un modèle YOLO entraîné sur une partie du dataset (validation ou test).

Affiche :
  1. les métriques d'Ultralytics : mAP50, mAP50-95, précision, rappel ;
  2. pour plusieurs seuils de confiance : vrais positifs, fausses détections, voitures manquées,
     précision, rappel et F1 (une détection est juste si elle recouvre une voiture à IoU ≥ 0,5).
     C'est le tableau qui sert à fixer ensemble le seuil de confiance de la démo ;
  3. quelques images annotées (vert : vraie voiture, rouge : détection + confiance) pour un contrôle visuel.

Tout ce qui est produit va dans iot/entrainement/sorties/ (ignoré par Git : images du dataset).

Utilisation (depuis la racine du dépôt, après l'entraînement sur Kaggle) :
    python iot/entrainement/evaluer_modele.py --modele iot/models/yolo_v1.pt
    python iot/entrainement/evaluer_modele.py --modele iot/models/yolo_v1.pt --partie test --seuils 0.3 0.5 0.7
Rappel : le test ne sert qu'à l'évaluation finale, pas à régler le modèle.
"""

import argparse
import random
import sys
from pathlib import Path

import cv2
import yaml
from ultralytics import YOLO

DOSSIER_IOT = Path(__file__).resolve().parent.parent
DATA_PAR_DEFAUT = DOSSIER_IOT / "dataset" / "data.yaml"
SORTIES = Path(__file__).resolve().parent / "sorties"
CLES = {"validation": "val", "test": "test"}   # partie -> clé de data.yaml et split d'Ultralytics
IOU_MIN = 0.5


def dossier_images(data_yaml, partie):
    d = yaml.safe_load(Path(data_yaml).read_text(encoding="utf-8"))
    racine = Path(d.get("path") or Path(data_yaml).parent)
    if not racine.is_absolute():
        racine = Path(data_yaml).parent / racine
    return racine / d[CLES[partie]]


def verites(image, w, h):
    """Boîtes réelles (x1, y1, x2, y2) en pixels, lues dans le .txt YOLO voisin."""
    lab = Path(str(image.parent).replace("images", "labels")) / f"{image.stem}.txt"
    boites = []
    for ligne in lab.read_text(encoding="utf-8").splitlines() if lab.exists() else []:
        v = ligne.split()
        if len(v) == 5:
            xc, yc, bw, bh = (float(x) for x in v[1:])
            boites.append(((xc - bw / 2) * w, (yc - bh / 2) * h, (xc + bw / 2) * w, (yc + bh / 2) * h))
    return boites


def iou(a, b):
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union > 0 else 0.0


def compter(predictions, verite, seuil):
    """Vrais positifs, fausses détections, manquées : appariement glouton par confiance décroissante."""
    detections = sorted((p for p in predictions if p[4] >= seuil), key=lambda p: -p[4])
    libres = list(range(len(verite)))
    vp = 0
    for d in detections:
        meilleure, idx = 0.0, None
        for i in libres:
            v = iou(d[:4], verite[i])
            if v > meilleure:
                meilleure, idx = v, i
        if idx is not None and meilleure >= IOU_MIN:
            vp += 1
            libres.remove(idx)
    return vp, len(detections) - vp, len(libres)


def dessiner(chemin_image, verite, predictions, seuil, sortie):
    im = cv2.imread(str(chemin_image))
    epaisseur = max(1, round(min(im.shape[:2]) / 300))
    for x1, y1, x2, y2 in verite:
        cv2.rectangle(im, (int(x1), int(y1)), (int(x2), int(y2)), (0, 200, 0), epaisseur)
    for x1, y1, x2, y2, c in predictions:
        if c >= seuil:
            cv2.rectangle(im, (int(x1), int(y1)), (int(x2), int(y2)), (0, 0, 255), epaisseur)
            cv2.putText(im, f"{c:.2f}", (int(x1), max(12, int(y1) - 4)), cv2.FONT_HERSHEY_SIMPLEX,
                        0.45, (0, 0, 255), 1, cv2.LINE_AA)
    cv2.imwrite(str(sortie / f"{chemin_image.stem}.jpg"), im)


def main():
    p = argparse.ArgumentParser(description="Évaluer un modèle YOLO sur la validation ou le test")
    p.add_argument("--modele", required=True, help="fichier .pt (ex. iot/models/yolo_v1.pt)")
    p.add_argument("--partie", choices=CLES, default="validation")
    p.add_argument("--data", default=str(DATA_PAR_DEFAUT), help="data.yaml du dataset découpé")
    p.add_argument("--seuils", type=float, nargs="+", default=[0.25, 0.4, 0.5, 0.6, 0.7],
                   help="seuils de confiance à comparer")
    p.add_argument("--apercu", type=int, default=8, help="nombre d'images annotées à enregistrer")
    p.add_argument("--conf-apercu", type=float, default=0.5, help="seuil utilisé pour dessiner les détections")
    p.add_argument("--imgsz", type=int, default=640)
    p.add_argument("--device", default="cpu", help="cpu, ou 0 pour un GPU")
    args = p.parse_args()

    modele_pt = Path(args.modele)
    if not modele_pt.exists():
        sys.exit(f"Modèle introuvable : {modele_pt}")
    images_dir = dossier_images(args.data, args.partie)
    images = sorted(f for f in images_dir.iterdir() if f.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".bmp"})
    if not images:
        sys.exit(f"Aucune image dans {images_dir}")
    sortie = SORTIES / f"{modele_pt.stem}_{args.partie}"
    sortie.mkdir(parents=True, exist_ok=True)
    modele = YOLO(str(modele_pt))

    # 1. Métriques d'Ultralytics (mAP calculé sur toutes les confiances)
    r = modele.val(data=args.data, split=CLES[args.partie], imgsz=args.imgsz, device=args.device,
                   project=str(sortie), name="ultralytics", exist_ok=True, verbose=False)
    print(f"\n=== {modele_pt.name} sur la partie « {args.partie} » ({len(images)} images) ===")
    print(f"mAP50     : {r.box.map50:.3f}")
    print(f"mAP50-95  : {r.box.map:.3f}")
    print(f"Précision : {r.box.mp:.3f}   Rappel : {r.box.mr:.3f}   (au seuil qui maximise le F1)")

    # 2. Tableau par seuil de confiance
    plus_bas = min(args.seuils + [args.conf_apercu])
    resultats = {}
    for chemin in images:
        res = modele.predict(str(chemin), conf=plus_bas, imgsz=args.imgsz, device=args.device, verbose=False)[0]
        h, w = res.orig_shape
        preds = [(*b, c) for b, c in zip(res.boxes.xyxy.tolist(), res.boxes.conf.tolist())]
        resultats[chemin] = (verites(chemin, w, h), preds)

    total_voitures = sum(len(v) for v, _ in resultats.values())
    print(f"\nVoitures réelles : {total_voitures} · une détection est juste si IoU ≥ {IOU_MIN}")
    print(f"{'seuil':>6} {'justes':>7} {'fausses':>8} {'manquées':>9} {'précision':>10} {'rappel':>7} {'F1':>6}")
    meilleur = None
    for s in sorted(args.seuils):
        vp = fp = fn = 0
        for verite, preds in resultats.values():
            a, b, c = compter(preds, verite, s)
            vp, fp, fn = vp + a, fp + b, fn + c
        prec = vp / (vp + fp) if vp + fp else 0.0
        rap = vp / (vp + fn) if vp + fn else 0.0
        f1 = 2 * prec * rap / (prec + rap) if prec + rap else 0.0
        print(f"{s:6.3f} {vp:7d} {fp:8d} {fn:9d} {prec:10.3f} {rap:7.3f} {f1:6.3f}")
        if meilleur is None or f1 > meilleur[1]:
            meilleur = (s, f1)
    print(f"Meilleur F1 parmi ces seuils : {meilleur[0]:.3f} (F1 = {meilleur[1]:.3f}) — à discuter en équipe.")

    # 3. Images annotées pour le contrôle visuel
    apercu = sortie / "apercu"
    apercu.mkdir(exist_ok=True)
    for f in apercu.glob("*.jpg"):
        f.unlink()
    for chemin in random.Random(0).sample(images, min(args.apercu, len(images))):
        verite, preds = resultats[chemin]
        dessiner(chemin, verite, preds, args.conf_apercu, apercu)
    print(f"\nImages annotées (vert : vérité, rouge : détection ≥ {args.conf_apercu}) : {apercu}")
    print(f"Courbes d'Ultralytics (PR, F1, matrice de confusion) : {sortie / 'ultralytics'}")


if __name__ == "__main__":
    main()
