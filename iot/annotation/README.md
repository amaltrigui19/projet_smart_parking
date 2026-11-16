# Annotation du dataset — scripts et procédures

Sprint 2 · Maissa (profil C) · mis à jour au sprint 3 (Eya)

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
