# Annotation du dataset — scripts et procédures

Sprint 2 · Maissa (profil C) · mis à jour au sprint 3 (Eya ; lot 3 synthétique et découpage : Amal)

Le dataset d'entraînement de YOLO a une classe unique, **`voiture`**, au format YOLO. Les images et les labels vont dans `iot/dataset/`, qui **n'est jamais versionné** (licences et droits d'auteur). Sources, licences et chiffres : voir [`../README.md`](../README.md#dataset--lot-1).

Le **lot 1** est terminé et rangé dans le dossier Google Drive de l'équipe (section 2) : on ne le reconstruit plus. Les scripts ci-dessous servent aux **lots suivants** : pré-annoter, corriger à la main, convertir, vérifier.

| Fichier | Rôle |
|---|---|
| `pre_annoter.py` | Pré-annote un dossier d'images avec YOLO-World (brouillon de boîtes à corriger) |
| `label_studio_config.xml` | Interface d'annotation Label Studio (une étiquette : `voiture`) |
| `lancer_label_studio.bat` | Lance Label Studio en local sur http://localhost:8081, avec l'accès aux images de `iot/dataset/` |
| `label_studio.py` | Passerelle YOLO ↔ Label Studio : `vers-ls` (import à corriger) et `depuis-ls` (export corrigé → YOLO) |
| `preparer_datasets.py` | Convertit un dossier YOLO vers la classe unique `voiture` et l'ajoute à un lot (`--sortie iot/dataset/lotN`) |
| `verifier_dataset.py` | Contrôle un lot (format, doublons) et affiche les statistiques ; code 1 en cas d'erreur |
| `generer_synthetique.py` | Génère le **lot 3 synthétique** : la maquette vue du dessus, avec des boîtes exactes (section 4) |
| `decouper_dataset.py` | Réunit les lots et les découpe en entraînement / validation / test, de façon stable (section 5) |
| `telecharger_roboflow.py` | Télécharge une source Roboflow du lot 1 (toy-car, CARPK) avec la clé de `iot/.env` (section 6) |
| `vues_toycar.csv`, `exclusions_toycar.txt`, `corrections/` | Tri et corrections manuelles des images toy-car du lot 1 (section 6) |
| `generer_lot2.py` | Génère le **lot 2 synthétique** : vraies voitures miniatures collées sur la maquette dessinée (section 6) |
| `exclusions_decoupes.txt` | Voitures découpées écartées par `generer_lot2.py` |
| `archiver_lot.py` | Archive un lot vérifié en `.zip` avec manifeste et empreintes, et le restaure en contrôlant chaque fichier |

Toutes les commandes se lancent **depuis la racine du dépôt** (cmd, PowerShell ou Git Bash).

---

## 1. Prérequis

```
pip install -r iot/requirements.txt
```

Le modèle `iot/yolov8s-world.pt` est téléchargé automatiquement par Ultralytics au premier lancement de `pre_annoter.py`.

---

## 2. Dossier partagé du dataset (Google Drive)

Chaque lot vérifié est déposé **une fois** dans le dossier Google Drive de l'équipe, **« Smart Parking - Dataset IoT »**, sous forme d'archive `.zip` datée. Le document `LISEZMOI - dataset` de ce dossier tient le journal des archives (date, contenu, nombre d'images et de voitures).

- **Accès réservé à l'équipe et à l'encadrant**, partagé par nom, **jamais par lien public** : CARPK est réservé à la recherche académique et les photos de test sont sous droits d'auteur.
- Une archive déposée n'est jamais modifiée : une nouvelle version = un nouveau fichier daté.

**Récupérer un lot** : télécharger le `.zip` depuis le Drive, l'extraire dans `iot/dataset/` (on obtient `iot/dataset/lot1/images` et `iot/dataset/lot1/labels`), puis vérifier :

```
python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot1 --apercu 12
```

Résultat attendu pour le lot 1 : `TOTAL 174 images, 5212 voitures` (`toycar` 73, `carpk` 80, `photos` 21), puis `Aucune erreur : le lot est prêt.` Les images de contrôle, avec leurs boîtes dessinées, sont dans `iot/dataset/apercu/`.

**Déposer un lot** : vérifier le lot (même commande, avec son dossier), le compresser en `.zip` (`lotN_<date>.zip`), le déposer dans le Drive et ajouter une ligne au journal du `LISEZMOI`.

---

## 3. Correction manuelle dans Label Studio

Les photos de `iot/photos_test/` viennent d'Internet (droits d'auteur) : on les corrige avec **Label Studio installé en local**. Rien ne sort du PC, contrairement à un projet Roboflow public. Les photos de la maquette passeront par la même procédure.

### 3.1 Installer (une seule fois)

Label Studio impose ses propres versions de nombreuses bibliothèques : on l'installe dans un **environnement Python séparé**, **en dehors du dépôt**, pour ne pas casser ultralytics.

```
python -m venv "%USERPROFILE%\venvs\label-studio"
"%USERPROFILE%\venvs\label-studio\Scripts\python.exe" -m pip install label-studio
```

Résultat attendu : `Successfully installed … label-studio-…` après quelques minutes. Vérifier avec `"%USERPROFILE%\venvs\label-studio\Scripts\label-studio.exe" --version`.

### 3.2 Préparer les pré-annotations

```
python iot/annotation/pre_annoter.py --entree iot/photos_test --sortie iot/dataset/pre_annotations/photos --format label-studio
```

Résultat attendu : `Images traitées : 21`, `Boîtes proposées : 58`, et le fichier `iot/dataset/pre_annotations/photos/pre_annotations.label-studio.json`.

### 3.3 Lancer Label Studio

Double-cliquer sur **`iot/annotation/lancer_label_studio.bat`**, ou le lancer depuis cmd. Le script autorise Label Studio à lire les images de `iot/dataset/` (*local files serving*) et range sa base de données dans `%USERPROFILE%\label-studio-data`, en dehors du dépôt.

Résultat attendu : la fenêtre affiche `Racine des images : …\iot\dataset`, puis `Label Studio is up and running on http://localhost:8081`. Ouvrir **http://localhost:8081** et créer un compte local (*Sign up* : un email et un mot de passe quelconques, stockés seulement sur le PC). Ne pas fermer la fenêtre noire tant qu'on travaille.

### 3.4 Créer le projet

1. **Create Project**. Nom : par exemple `Lot 2 - maquette`.
2. Onglet **Labeling Setup** → **Custom template** (ou *Code*) : coller le contenu de `iot/annotation/label_studio_config.xml` → **Save**.
   Ne pas changer les noms `image` et `label` : les scripts les utilisent.
3. **Settings** → **Cloud Storage** → **Add Source Storage** :
   - *Storage Type* : **Local files** ;
   - *Absolute local path* : `<chemin du dépôt>\iot\dataset\pre_annotations\photos\images` (par exemple `C:\Users\<vous>\…\projet_smart_parking\iot\dataset\pre_annotations\photos\images`). Le chemin doit se trouver sous la racine affichée au lancement ;
   - **Add Storage**, puis **Check Connection**, qui doit afficher « Successfully connected ».
   - **Ne PAS cliquer sur *Sync Storage*** : cela créerait 21 tâches **sans** les pré-annotations. Ce stockage sert seulement à autoriser l'affichage des images.
4. Revenir au projet → **Import** → choisir `iot\dataset\pre_annotations\photos\pre_annotations.label-studio.json` → **Import**.
   Résultat attendu : 21 tâches. Dans la colonne *Predictions*, la plupart en ont 1.

### 3.5 Corriger

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

### 3.6 Exporter et ajouter au lot

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

---

## 4. Lot 3 synthétique (sprint 3)

Le matériel de la maquette manque encore : le lot 3 est **généré** par `generer_synthetique.py`, en attendant les vraies photos.

Le script dessine des images **640 × 480** de la maquette **vue du dessus**, avec la géométrie exacte du contrat (§4.3) : 60 × 40 cm, places A1 à B4 de 8 × 13 cm, allée centrale, zone interdite `allee1` hachurée, marquage PMR sur A1 et électrique sur B4. Il y place des voitures miniatures. Les boîtes sont calculées pendant le dessin, à partir du masque de chaque voiture : **elles sont exactes, il n'y a rien à corriger dans Label Studio**.

| Ce qui varie | Pourquoi |
|---|---|
| Position, hauteur et rotation (± 4°) de la caméra | L'emplacement définitif de la caméra n'est pas encore connu |
| Nombre, couleur, taille (7 à 10 cm) et orientation des voitures | Variété ; pas de voiture noire (protocole de `../maquette.md`) |
| Voiture à cheval sur deux places (≈ 17 % des images) | Prépare la détection de l'anomalie `cheval` |
| Voiture dans la zone interdite (≈ 15 %) | Prépare l'anomalie `zone_interdite` |
| Voiture qui roule dans l'allée (≈ 15 %) | Une voiture n'est pas toujours garée |
| Parking vide (≈ 7 %) | Exemples négatifs : YOLO apprend à ne rien détecter |
| Sol, lumière, ombres, flou, bruit, compression JPEG | Imite les défauts d'une vraie webcam |

### 4.1 Générer et vérifier

```
python iot/annotation/generer_synthetique.py --nombre 300
python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot3 --apercu 12
```

Résultats attendus : `Images écrites : 300`, `Voitures (boîtes) écrites : 1382`, puis `TOTAL 300 images, 1382 voitures` et `Aucune erreur : le lot est prêt.` Contrôler à l'œil les images de `iot/dataset/apercu/` : chaque voiture a une boîte ajustée, aucune boîte sur une zone vide.

- **Reproductible** : la même graine (`--graine 3`, par défaut) donne les mêmes images sur tous les PC.
- **Relancer remplace le lot** : `iot/dataset/lot3/images` et `labels` sont recréés, sans doublons.
- Noms de fichiers : `synth_lot3_00001.jpg`… (préfixe `synth_`, avec `lot3` pour ne pas croiser les noms du lot 2).
- `iot/dataset/lot3/scenarios.csv` décrit chaque image (`normal`, `vide`, `cheval`, `zone_interdite`) : ces images serviront de **cas de test pour la détection des anomalies**.

### 4.2 Archiver

```
Compress-Archive -Path iot\dataset\lot3 -DestinationPath iot\dataset\lot3_<date>.zip
(Get-FileHash iot\dataset\lot3_<date>.zip -Algorithm SHA256).Hash.Substring(0,8).ToLower()
```

Déposer le `.zip` dans le dossier Google Drive de l'équipe et ajouter une ligne au journal du `LISEZMOI` (date, fichier, 300 images, 1382 voitures, contenu, auteur, début de l'empreinte SHA-256).

### 4.3 Quand la maquette sera prête

Les vraies photos de la maquette compléteront le lot 3 (préfixe `maquette_`), avec la chaîne des sections 3.2 à 3.6 : pré-annotation par YOLO-World, correction dans Label Studio, puis `preparer_datasets.py --sortie iot/dataset/lot3 --nom maquette --seuil-doublons 0`. Les images synthétiques restent dans le dataset, en complément.

---

## 5. Découper le dataset : entraînement, validation, test

Les lots sont réunis puis découpés en trois parties, **sans modifier les lots** :

| Partie | Part | Rôle |
|---|---|---|
| `entrainement/` | 70 % | YOLO apprend sur ces images |
| `validation/` | 15 % | Suivi pendant l'entraînement : choix du meilleur modèle, détection du sur-apprentissage |
| `test/` | 15 % | **Jamais vu pendant l'entraînement** : évaluation finale du modèle (sprint 9) |

### 5.1 Préparer et découper

1. Récupérer les lots 1 et 2 depuis le Drive et les extraire dans `iot/dataset/` (ou les reconstruire, section 6) ; générer le lot 3 (section 4.1).
2. Découper, puis vérifier chaque partie :
   ```
   python iot/annotation/decouper_dataset.py
   python iot/annotation/verifier_dataset.py --dossier iot/dataset/entrainement
   python iot/annotation/verifier_dataset.py --dossier iot/dataset/validation
   python iot/annotation/verifier_dataset.py --dossier iot/dataset/test
   ```
   Le script affiche le nombre d'images par source et par partie, et écrit la liste complète dans `iot/dataset/decoupage.csv`.
3. Copier `iot/dataset/data.yaml.exemple` en `iot/dataset/data.yaml` (il pointe vers les trois dossiers).

### 5.2 Pourquoi un découpage par empreinte du nom

Chaque image est placée d'après l'empreinte SHA-256 de son **nom**, et non par un tirage au hasard :
- **même découpage sur tous les PC**, sans graine à partager ;
- **stable quand on ajoute des images** : avec les photos réelles de la maquette (`--lots lot1 lot2 lot3 lot4`, ou un lot 3 complété), aucune image déjà placée ne change de partie. Une image de test reste en test : le modèle ne la verra jamais à l'entraînement ;
- **chaque source est répartie dans les mêmes proportions** (toycar, carpk, synth, maquette…).

Le script refuse deux images de même nom dans deux lots : les préfixes de fichiers doivent rester uniques (`synth_lot3_`, `maquette_`…). Les variantes d'une même photo ne doivent pas être séparées : le lot 1 n'en garde déjà qu'une par photo d'origine.
---

## 6. Reconstruire le dataset depuis zéro

Le dataset n'est pas versionné : n'importe quel membre de l'équipe (ou un ingénieur qui nous rejoint) doit pouvoir le **reconstruire à tout moment**. Les lots dépendent les uns des autres, il faut donc suivre cet ordre :

```
lot 1 (Drive ou Roboflow)  ->  découpes des voitures  ->  lot 2  ->  lot 3  ->  découpage train / val / test
```

| Lot | Auteur | Contenu | Reconstruction | Résultat attendu (`verifier_dataset.py`) |
|---|---|---|---|---|
| 1 | Maissa | toy-car, CARPK, photos de test | Archive du Drive (recommandé) ou Roboflow + Label Studio | `TOTAL 174 images, 5212 voitures` |
| 2 | Eya | 300 scènes synthétiques, voitures réelles collées | `generer_lot2.py`, graine 0 | `TOTAL 300 images, 1250 voitures` |
| 3 | Amal | 300 scènes synthétiques dessinées | `generer_synthetique.py`, graine 3 | `TOTAL 300 images, 1382 voitures` |

Les lots 2 et 3 sont **reproductibles au bit près** : même graine, mêmes images, mêmes labels, sur tous les PC (vérifié le 23/11/2026 en régénérant le lot 2 et en comparant les empreintes des 300 images avec l'archive `lot2_2026-11-16.zip`).

### 6.1 Lot 1

**Le plus simple** : télécharger `lot1_<date>.zip` depuis le Drive (section 2) et le restaurer, ce qui contrôle chaque fichier avec le manifeste :

```
python iot/annotation/archiver_lot.py restaurer --zip "<chemin>/lot1_2026-11-16_toycar-carpk.zip"
python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot1
```

**Sans le Drive** (depuis les sources d'origine). Il faut une clé API Roboflow dans `iot/.env` (copier `iot/.env.exemple`) :

```
python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno --version 4 --sortie iot/dataset/sources/toycar_v4
python iot/annotation/telecharger_roboflow.py --projet <votre-workspace>/carpk-2-xxxxx --version 1 --sortie iot/dataset/sources/carpk_v1
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/toycar_v4 --nom toycar --classes-a-garder toycar --une-variante-par-origine --ignorer-vides --exclusions iot/annotation/exclusions_toycar.txt --corrections iot/annotation/corrections
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/carpk_v1 --nom carpk --echantillon 80
```

Le projet CARPK est un fork dans son propre workspace Roboflow (voir l'historique Git de ce README, commit `66508a8^`, pour la procédure détaillée). Les 21 photos de test (`photos_`) se refont avec la chaîne de la section 3 : **c'est la seule partie manuelle**, d'où l'intérêt de l'archive du Drive.

### 6.2 Lot 2

Il a besoin des images `toycar_` du lot 1 (pour y découper les voitures) :

```
python iot/annotation/generer_lot2.py decouper
python iot/annotation/generer_lot2.py generer --nombre 300 --graine 0 --sortie iot/dataset/sources/synth_v1 --remplacer
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/synth_v1 --nom synth --sortie iot/dataset/lot2 --seuil-doublons 0
python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot2 --apercu 12
```

- `decouper` produit 18 voitures détourées dans `iot/dataset/synthetique/voitures/` et une planche de contrôle ; 4 découpes sont écartées (`exclusions_decoupes.txt`).
- `generer` écrit aussi `scenes.jsonl` : vérité terrain de chaque image (places recouvertes, zone interdite, homographie cm → pixels).
- Optionnel : `python iot/annotation/generer_lot2.py plan` produit la vue de la caméra à vide, le `places.json` correspondant et le plan de la maquette à imprimer en A4.

### 6.3 Lot 3 et découpage

```
python iot/annotation/generer_synthetique.py --nombre 300
python iot/annotation/decouper_dataset.py
```

Voir les sections 4.1 et 5.1 pour les vérifications.
