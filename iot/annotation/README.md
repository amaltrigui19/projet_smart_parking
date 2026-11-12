# Annotation du dataset — scripts et procédures

Sprint 2 · Maissa (profil C)

Ce dossier contient les scripts qui fabriquent le dataset d'entraînement de YOLO (classe unique **`voiture`**) et la procédure pour le **reproduire avec sa propre clé API**. Les images et les labels produits vont dans `iot/dataset/`, qui **n'est jamais versionné** (licences et droits d'auteur). Sources, licences et chiffres : voir [`../README.md`](../README.md#dataset--lot-1).

| Fichier | Rôle |
|---|---|
| `telecharger_roboflow.py` | Télécharge une version d'un dataset Roboflow au format YOLO, avec la clé de `iot/.env` |
| `pre_annoter.py` | Pré-annote un dossier d'images avec YOLO-World (brouillon de boîtes à corriger) |
| `preparer_datasets.py` | Convertit un dataset vers la classe unique `voiture` et l'ajoute au lot (`iot/dataset/lot1/`) |
| `verifier_dataset.py` | Contrôle le lot (format, doublons) et affiche les statistiques ; code 1 en cas d'erreur |
| `label_studio_config.xml` | Interface d'annotation Label Studio (une étiquette : `voiture`) |
| `exclusions_toycar.txt` | Images de toy-car-detection exclues, avec la raison |
| `vues_toycar.csv` | Vue de chaque image de toy-car-detection retenue : dessus, plongée ou côté |
| `corrections/` | Labels corrigés à la main (un `.txt` YOLO par image, **sans image**), réappliqués par `--corrections` |
| `label_studio.py` | Passerelle YOLO ↔ Label Studio : `vers-ls` (import à corriger) et `depuis-ls` (export corrigé → YOLO) |
| `lancer_label_studio.bat` | Lance Label Studio en local sur http://localhost:8081, avec l'accès aux images de `iot/dataset/` |

Toutes les commandes se lancent **depuis la racine du dépôt** (cmd, PowerShell ou Git Bash).

---

## 1. Prérequis

```
pip install -r iot/requirements.txt
```

Le modèle `iot/yolov8s-world.pt` est téléchargé automatiquement par Ultralytics au premier lancement de `pre_annoter.py`.

### Clé API Roboflow

1. Créer un compte sur https://roboflow.com (plan gratuit). **Attention : en plan gratuit, tous les projets du workspace sont publics.**
2. Récupérer la clé : *Settings* (en haut à droite) → *API Keys* → *Private API Key* → *Copy*.
3. Copier le modèle de fichier, puis y coller la clé :
   ```
   copy iot\.env.exemple iot\.env        (cmd)
   cp iot/.env.exemple iot/.env           (Git Bash)
   ```
   Résultat attendu : `iot/.env` contient `ROBOFLOW_API_KEY=<votre clé>`. Vérifier que Git l'ignore : `git check-ignore iot/.env` doit afficher `iot/.env`.
4. Tester :
   ```
   python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno --info
   ```
   Résultat attendu :
   ```
   Projet  : parkitnowprototype/toy-car-detection-f5tno (toy-car-detection)
   Licence : CC BY 4.0
   Images  : 107 | classes (nombre de boîtes) : {'objects': 1, 'toycar': 83}
     version 4 : 317 images, répartition {'valid': 2, 'train': 315}
     version 3 : 128 images, répartition {'valid': 2, 'train': 126}
   ```

La clé ne doit **jamais** apparaître dans le code, dans un commit ni dans une capture d'écran. Si elle a été montrée, la régénérer : *Settings* → *API Keys* → *Regenerate*.

---

## 2. Télécharger toy-car-detection

Projet public, version 4 exportable directement :

```
python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno --version 4 --sortie iot/dataset/sources/toycar_v4
```

Résultat attendu : `Téléchargé : 18740 Ko`, `train : 315 images`, `valid : 2 images`, `names: ['objects', 'toycar']`.

Ce qu'il faut savoir sur cette version (vérifié image par image au sprint 2) :
- **augmentée × 3** : 315 images, mais 107 images d'origine. Chaque photo existe en 3 variantes (retournements, rotation de ±5°), étirées en 640 × 640 ;
- annotations en **polygones**, et non en rectangles ;
- la classe **`objects`** apparaît sur une seule photo et **double une voiture déjà annotée** (recouvrement de 99 %) : elle est supprimée ;
- 31 photos d'origine n'ont **aucune voiture** (pièces, écrans, visage d'une personne) : elles sont ignorées ;
- 3 photos sont exclues (`exclusions_toycar.txt`) : 2 captures d'écran avec des boîtes déjà dessinées, et 1 photo avec un visage.

---

## 3. Télécharger CARPK (fork sur Roboflow Universe)

Le projet public `anis-elahi/carpk-2` n'a **aucune version générée** : on ne peut pas l'exporter directement. Il faut le copier (*fork*) dans son workspace, générer une version, la télécharger, puis **supprimer le fork**.

> **Pourquoi supprimer le fork ?** CARPK et PUCPR+ sont réservés par leurs auteurs à la **recherche académique** (voir les licences dans `../README.md`). En plan gratuit, un fork est public : le garder reviendrait à rediffuser ces images.

1. **Fork.** Ouvrir https://universe.roboflow.com/anis-elahi/carpk-2, se connecter, cliquer sur **Fork Dataset** (en haut à droite), puis choisir son workspace.
   Résultat : un projet « CARPK 2 » apparaît dans son workspace sur https://app.roboflow.com (391 images).
2. **Générer une version sans transformation.** Dans le projet : menu de gauche **Versions** → **Generate New Version** (ou **Generate**).
   - *Train/Test Split* : garder la répartition proposée (le script réunit train, valid et test de toute façon).
   - *Preprocessing* : **supprimer toutes les étapes** (Auto-Orient, Resize…) avec la corbeille de chaque ligne.
   - *Augmentation* : **n'en ajouter aucune**.
   - **Create**. Résultat : une version **v1** de 391 images.
3. **Trouver l'identifiant du projet.** Le fork reçoit un suffixe aléatoire (le nôtre s'appelait `carpk-2-3tfiu`) :
   ```
   python iot/annotation/telecharger_roboflow.py --mes-projets
   ```
   Résultat attendu, par exemple : `<votre-workspace>/carpk-2-xxxxx | CARPK 2 | 391 images | 1 version(s) | classes {'0': 25894}`.
4. **Télécharger la version 1** (remplacer l'identifiant par le sien) :
   ```
   python iot/annotation/telecharger_roboflow.py --projet <votre-workspace>/carpk-2-xxxxx --version 1 --sortie iot/dataset/sources/carpk_v1
   ```
   Le message `Export en préparation chez Roboflow…` peut s'afficher plusieurs fois : c'est normal. Résultat attendu : `Téléchargé : environ 73000 Ko`, `train : 274`, `valid : 78`, `test : 39` (la répartition peut varier), `names: ['0']`.
5. **Vérifier la classe.** Le fichier `data.yaml` téléchargé doit indiquer `nc: 1` et `names: ['0']`. Le projet d'origine affichait une classe `1` (48 boîtes) : elle n'existe plus dans le fork. Si elle réapparaît, la retirer avec `--classes-a-garder 0` à l'étape suivante.
6. **Supprimer le fork.** Dans le projet : **Settings** → **Delete Project**, puis taper le nom pour confirmer.
   Vérification : `python iot/annotation/telecharger_roboflow.py --mes-projets` ne liste plus le projet.

Contenu de ce projet : **363 images CARPK** (prises par drone, environ 62 voitures par image) et **28 images PUCPR+** (caméra fixe en hauteur, environ 116 voitures par image). Images en 1280 × 720, rectangles uniquement. 7 boîtes de taille nulle sont ignorées par le script.

---

## 4. Construire le lot 1

```
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/toycar_v4 --nom toycar --classes-a-garder toycar --une-variante-par-origine --ignorer-vides --exclusions iot/annotation/exclusions_toycar.txt --corrections iot/annotation/corrections
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/carpk_v1 --nom carpk --echantillon 80
python iot/annotation/preparer_datasets.py --source iot/dataset/corrections/photos --nom photos --seuil-doublons 0
```

Résultats attendus :
- toycar : `Images écrites : 73`, `Voitures (boîtes) écrites : 76`, `Corrections manuelles : 1` ;
- carpk : `Images écrites : 80`, `Voitures (boîtes) écrites : 5059` (graine 42) ;
- photos : `Images écrites : 21`, `Voitures (boîtes) écrites : 77`. Cette ligne n'est possible qu'avec nos photos de test, qui ne sont pas partagées (droits d'auteur), et après la section 5.

L'échantillon est tiré après un **tri par nom d'origine**. Les codes `.rf.…` de Roboflow et la répartition train/valid/test, qui changent d'un fork à l'autre, n'ont donc aucun effet : la même graine donne les mêmes 80 images.

Vérifier :

```
python iot/annotation/verifier_dataset.py --apercu 12
```

Résultat attendu : `TOTAL 174 images, 5212 voitures`, puis `Aucune erreur : le lot est prêt.` Les images de contrôle, avec leurs boîtes dessinées, sont dans `iot/dataset/apercu/`.

### Erreurs d'annotation repérées et corrigées

| Image du lot | Problème | Correction |
|---|---|---|
| `toycar_toy-car-21-Copy_jpeg` | Seule la voiture bleue était annotée ; **la camionnette rouge à l'arrière-plan était oubliée** | Boîte ajoutée (proposée par YOLO-World, confiance 0,81, puis vérifiée à l'œil) dans `corrections/toycar_toy-car-21-Copy_jpeg.txt` |

---

## 5. Correction manuelle dans Label Studio

Les photos de `iot/photos_test/` viennent d'Internet (droits d'auteur) : on les corrige avec **Label Studio installé en local**. Rien ne sort du PC, contrairement à un projet Roboflow public. Les photos de la maquette passeront par la même procédure.

### 5.1 Installer (une seule fois)

Label Studio impose ses propres versions de nombreuses bibliothèques : on l'installe dans un **environnement Python séparé**, **en dehors du dépôt**, pour ne pas casser ultralytics.

```
python -m venv "%USERPROFILE%\venvs\label-studio"
"%USERPROFILE%\venvs\label-studio\Scripts\python.exe" -m pip install label-studio
```

Résultat attendu : `Successfully installed … label-studio-…` après quelques minutes. Vérifier avec `"%USERPROFILE%\venvs\label-studio\Scripts\label-studio.exe" --version`.

### 5.2 Préparer les pré-annotations

```
python iot/annotation/pre_annoter.py --entree iot/photos_test --sortie iot/dataset/pre_annotations/photos --format label-studio
```

Résultat attendu : `Images traitées : 21`, `Boîtes proposées : 58`, et le fichier `iot/dataset/pre_annotations/photos/pre_annotations.label-studio.json`.

### 5.3 Lancer Label Studio

Double-cliquer sur **`iot/annotation/lancer_label_studio.bat`**, ou le lancer depuis cmd. Le script autorise Label Studio à lire les images de `iot/dataset/` (*local files serving*) et range sa base de données dans `%USERPROFILE%\label-studio-data`, en dehors du dépôt.

Résultat attendu : la fenêtre affiche `Racine des images : …\iot\dataset`, puis `Label Studio is up and running on http://localhost:8081`. Ouvrir **http://localhost:8081** et créer un compte local (*Sign up* : un email et un mot de passe quelconques, stockés seulement sur le PC). Ne pas fermer la fenêtre noire tant qu'on travaille.

### 5.4 Créer le projet

1. **Create Project**. Nom : `Lot 1 - photos`.
2. Onglet **Labeling Setup** → **Custom template** (ou *Code*) : coller le contenu de `iot/annotation/label_studio_config.xml` → **Save**.
   Ne pas changer les noms `image` et `label` : les scripts les utilisent.
3. **Settings** → **Cloud Storage** → **Add Source Storage** :
   - *Storage Type* : **Local files** ;
   - *Absolute local path* : `C:\Users\<vous>\Downloads\smart-parking-iot-S1\iot\dataset\pre_annotations\photos\images`. Le chemin doit se trouver sous la racine affichée au lancement ;
   - **Add Storage**, puis **Check Connection**, qui doit afficher « Successfully connected ».
   - **Ne PAS cliquer sur *Sync Storage*** : cela créerait 21 tâches **sans** les pré-annotations. Ce stockage sert seulement à autoriser l'affichage des images.
4. Revenir au projet → **Import** → choisir `iot\dataset\pre_annotations\photos\pre_annotations.label-studio.json` → **Import**.
   Résultat attendu : 21 tâches. Dans la colonne *Predictions*, la plupart en ont 1.

### 5.5 Corriger

Ouvrir la première tâche (**Label All Tasks**). Les boîtes de YOLO-World s'affichent ; un clic sur l'une d'elles la transforme en annotation modifiable.

| Action | Comment |
|---|---|
| **Supprimer** une fausse détection | Cliquer sur la boîte, puis touche `Suppr` (ou `Backspace`) |
| **Ajouter** une voiture oubliée | Touche `1` (étiquette `voiture`), puis tracer le rectangle à la souris |
| **Ajuster** une boîte | Tirer sur ses coins : le rectangle doit coller aux bords de la voiture, roues et rétroviseurs compris |
| **Valider** l'image | **Submit** (ou `Ctrl+Entrée`), même si aucune voiture n'est visible : une image vide validée est un exemple utile |

Règles d'annotation :
- une boîte **par voiture**, même si elle est en partie cachée ou coupée par le bord de l'image ;
- rectangle droit, **sans rotation** (le format YOLO ne garde pas la rotation) ;
- pas de boîte sur les personnes, les objets ni les reflets.

À vérifier en priorité : `11.jpg` et `25.webp`, sur lesquelles YOLO-World n'a rien trouvé. Il faut aussi relire les boîtes de confiance faible (≈ 0,2), qui sont souvent des erreurs.

### 5.6 Exporter et ajouter au lot

1. Dans le projet : **Export** → format **JSON** (pas *JSON-MIN*, ni *YOLO*) → **Export**. Enregistrer le fichier dans `iot\dataset\exports\photos.label-studio.json` (dossier ignoré par Git).
2. Convertir en YOLO, puis ajouter au lot :
   ```
   python iot/annotation/label_studio.py depuis-ls --export iot/dataset/exports/photos.label-studio.json --sortie iot/dataset/corrections/photos
   python iot/annotation/preparer_datasets.py --source iot/dataset/corrections/photos --nom photos --seuil-doublons 0
   python iot/annotation/verifier_dataset.py --apercu 12
   ```
   Résultat attendu : `Images corrigées écrites : 21`, puis une ligne `photos` dans le tableau de vérification, et `Aucune erreur`.
   Si le script signale des « tâches sans annotation validée », retourner dans Label Studio et cliquer sur **Submit** sur ces images.

`--seuil-doublons 0` : les boîtes ont été relues par un humain, on n'en fusionne aucune.

### 5.7 Corriger quelques annotations de toy-car-detection

Exemple avec l'oubli repéré sur `toycar_toy-car-21-Copy_jpeg` (camionnette rouge non annotée) :

1. Créer le fichier d'import à partir des labels existants (on peut donner plusieurs noms) :
   ```
   python iot/annotation/label_studio.py vers-ls --dossier iot/dataset/lot1 --fichiers toycar_toy-car-21-Copy_jpeg --sortie iot/dataset/exports/corrections_toycar.label-studio.json
   ```
2. Dans Label Studio, créer un second projet `Lot 1 - corrections toy-car`, avec la même interface (5.4, étape 2). Ajouter un stockage *Local files* sur `…\iot\dataset\lot1\images`, sans *Sync*. Importer le fichier JSON.
3. Corriger (ajouter la camionnette rouge), puis **Submit**.
4. Exporter en **JSON** dans `iot\dataset\exports\corrections_toycar.json`, convertir, puis **ranger les `.txt` corrigés dans `iot/annotation/corrections/`** (versionné : texte seulement) :
   ```
   python iot/annotation/label_studio.py depuis-ls --export iot/dataset/exports/corrections_toycar.json --sortie iot/dataset/exports/corrections_toycar --labels-seulement
   copy iot\dataset\exports\corrections_toycar\labels\*.txt iotnnotation\corrections\n   ```
5. Reconstruire la partie toycar du lot (commande de la section 4, avec `--corrections`), puis vérifier :
   ```
   python iot/annotation/verifier_dataset.py
   ```
   Résultat attendu : `Corrections manuelles : 1 (toycar_toy-car-21-Copy_jpeg)` (ou plus), puis `Aucune erreur`.

Comme les corrections sont versionnées, elles sont réappliquées à chaque reconstruction du lot depuis Roboflow.

> Pour une seule boîte oubliée, on peut aussi faire proposer la boîte par YOLO-World, la vérifier en la dessinant, puis écrire la ligne YOLO dans `corrections/<image>.txt`. C'est ce qui a été fait pour `toy-car-21`.
