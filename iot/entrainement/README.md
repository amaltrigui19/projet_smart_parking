# Entraînement YOLO (sprint 4)

YOLO11n est entraîné sur **Kaggle**, qui fournit un GPU gratuit (pas de GPU sur nos PC), puis évalué en local.

| Fichier | Rôle |
|---|---|
| `entrainer_yolo_v1.ipynb` | Notebook Kaggle : vérifie le GPU, prépare le dataset, entraîne YOLO11n, affiche les courbes et les métriques (validation, puis **une seule fois** le test), range les fichiers à télécharger |
| `evaluer_modele.py` | En local : métriques d'un `.pt` sur la validation ou le test, tableau précision / rappel / F1 par seuil de confiance, images annotées |

## 1. Préparer le dataset (en local)

Procédure complète : [`../annotation/README.md`](../annotation/README.md), section 5. En résumé : extraire les lots 1 et 2 du Drive dans `iot/dataset/`, générer le lot 3, découper, vérifier, puis `copy iot\dataset\data.yaml.exemple iot\dataset\data.yaml`.

Le zip pour Kaggle ne contient que `data.yaml`, `entrainement/`, `validation/` et `test/` (sprint 4 : 774 images, 7 844 voitures, environ 55 Mo) :

```powershell
Compress-Archive -Path iot\dataset\data.yaml, iot\dataset\entrainement, iot\dataset\validation, iot\dataset\test -DestinationPath $HOME\Downloads\dataset_smartparking_s4.zip
```

Le zip reste **hors du dépôt** : il contient des images CARPK (usage académique uniquement) et des photos sous droits d'auteur.

## 2. Entraîner sur Kaggle

1. **Dataset privé** : *Datasets* → *New Dataset* → déposer `dataset_smartparking_s4.zip`. Visibilité **Private**, jamais *Public* (licence de CARPK).
2. **Notebook** : *Code* → *New Notebook* → *File* → *Import Notebook* → `iot/entrainement/entrainer_yolo_v1.ipynb`.
3. *Add Input* → *Your Datasets* → le dataset de l'étape 1. Le notebook le trouve tout seul dans `/kaggle/input/`.
4. *Settings* : *Accelerator* → **GPU T4 x2** (ou P100) ; *Internet* → **On** (pour `pip install` et le téléchargement de `yolo11n.pt`). Le compte Kaggle doit être vérifié par téléphone pour avoir le GPU.
5. **Run All**. Les hyperparamètres sont dans la première cellule de code (50 epochs, `patience=10`, `imgsz=640`, `seed=42`).
6. À la fin, onglet **Output** → dossier `yolo_v1/` : télécharger `best.pt`, `results.csv`, `results.png`, `confusion_matrix.png` et `metriques.json`.

La dernière cellule supprime la copie du dataset et les dossiers d'entraînement de `/kaggle/working/`, pour qu'aucune image ne reste dans la version enregistrée du notebook. Garder le notebook **privé**.

## 3. Ranger le modèle et l'évaluer

**Modèle actuel : [`iot/models/yolo_v1.pt`](../models/yolo_v1.pt)**. Sa fiche (paramètres, métriques, courbes, limites) est dans [`iot/models/yolo_v1/README.md`](../models/yolo_v1/README.md).

Pour un nouveau modèle :

```powershell
New-Item -ItemType Directory -Force iot\models | Out-Null
copy $HOME\Downloads\best.pt iot\models\yolo_v1.pt
python iot/entrainement/evaluer_modele.py --modele iot/models/yolo_v1.pt
```

- `iot/models/*.pt` est **versionné** (exception dans `.gitignore`). Un YOLO11n pèse environ 5,5 Mo. Les courbes et `metriques.json` peuvent aller dans `iot/models/yolo_v1/`.
- `evaluer_modele.py` affiche les métriques sur la **validation** et le tableau par seuil de confiance, qui sert à **fixer ensemble le seuil** de la démo. Il enregistre des images annotées dans `iot/entrainement/sorties/` (ignoré par Git).
- Le **test** (`--partie test`) ne sert qu'à l'évaluation finale : on ne règle plus rien d'après lui.
