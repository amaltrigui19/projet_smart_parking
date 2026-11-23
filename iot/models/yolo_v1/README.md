# Modèle YOLO v1 (sprint 4)

Fichier : [`../yolo_v1.pt`](../yolo_v1.pt) (5,5 Mo). Détecteur YOLO11n à une seule classe, `voiture` (identifiant 0).
Entraîné sur Kaggle (GPU) avec [`iot/entrainement/entrainer_yolo_v1.ipynb`](../../entrainement/entrainer_yolo_v1.ipynb), le 22 novembre 2026.

## Entraînement

| Paramètre | Valeur |
|---|---|
| Modèle de départ | `yolo11n.pt` (pré-entraîné sur COCO) |
| Epochs | 50 (maximum), `patience=10` : pas d'arrêt anticipé, les 50 ont tourné |
| Taille d'image | 640 |
| Batch | 16 |
| Graine | 42 |
| Ultralytics | 8.4.216 (Kaggle) |

**Dataset** : les lots 1, 2 et 3 réunis, soit 774 images et 7 844 voitures, découpés en 70 / 15 / 15 :

| Partie | Images | Voitures |
|---|---|---|
| Entraînement | 543 | 5 544 |
| Validation | 112 | 757 |
| Test | 119 | 1 543 |

Par source : 600 images synthétiques (`synth_`), 80 CARPK, 73 toy-car-detection et 21 photos (`photos_`).

## Métriques

| Partie | mAP50 | mAP50-95 | Précision | Rappel |
|---|---|---|---|---|
| Validation | 0,995 | 0,899 | 0,992 | 0,991 |
| Test (évalué une seule fois) | 0,982 | 0,784 | 0,976 | 0,959 |

Validation, par seuil de confiance (une détection est juste si IoU ≥ 0,5), mesurée avec `evaluer_modele.py` :

| Seuil | Justes | Fausses | Manquées | Précision | Rappel | F1 |
|---|---|---|---|---|---|---|
| 0,25 | 753 | 18 | 4 | 0,977 | 0,995 | 0,986 |
| 0,40 | 753 | 10 | 4 | 0,987 | 0,995 | **0,991** |
| 0,50 | 750 | 8 | 7 | 0,989 | 0,991 | 0,990 |
| 0,60 | 747 | 8 | 10 | 0,989 | 0,987 | 0,988 |
| 0,70 | 739 | 3 | 18 | 0,996 | 0,976 | 0,986 |

Le seuil de confiance de la démo sera fixé en équipe.

## Lecture des courbes ([`results.png`](results.png))

- Les pertes d'entraînement et de validation baissent régulièrement du début à la fin.
- Il n'y a pas de surapprentissage : les pertes de validation ne remontent pas.
- Le modèle progresse encore à l'epoch 50 : le mAP50-95 de validation passe de 0,865 (epoch 20) à 0,896 (epoch 49, la meilleure). Le mAP50 plafonne à 0,995 dès l'epoch 20.

## Limites

- **600 images synthétiques sur 774** : les très bons scores reflètent surtout ce type d'image.
- **Le test est plus bas en mAP50-95** (0,784 contre 0,899) : les boîtes sont moins précises sur des images jamais vues. Cause probable : le test contient beaucoup plus de petites voitures CARPK que la validation (274 petites boîtes contre 15).
- **Pas encore de photos de la vraie maquette** : rien ne garantit les performances dans les conditions de la démo (caméra du Pi, éclairage, angle).

## Pistes pour la v2 (Eya)

- **Plus d'epochs** (par exemple 100, avec `patience`), puisque le mAP50-95 progresse encore.
- **Analyse des erreurs** sur la validation (`evaluer_modele.py`, images annotées) : fausses détections, voitures manquées, petites boîtes.
- **Photos réelles** de la maquette avec la caméra du Pi, ajoutées à l'entraînement et à la validation.

> ⚠️ **Le jeu de test ne doit pas servir à régler la v2.** Il a déjà été utilisé une fois pour la v1. Tous les choix de la v2 (epochs, seuils, données) se font sur la **validation**. Le test ne sert qu'à l'évaluation finale.

## Fichiers

| Fichier | Contenu |
|---|---|
| `results.csv`, `results.png` | Pertes et métriques à chaque epoch |
| `confusion_matrix.png` | Matrice de confusion sur la validation |
| `metriques.json` | Hyperparamètres et métriques (validation et test), écrits par le notebook |

Pour évaluer le modèle en local (sur la validation uniquement) :

```powershell
python iot/entrainement/evaluer_modele.py --modele iot/models/yolo_v1.pt
```
