# Partie IoT & IA — Détection des places par caméra

Responsable : Maissa Ben Mrad

Cette partie tourne sur le **Raspberry Pi 4** installé au-dessus du parking.
Elle joue le rôle des « yeux » du système :

```
1. REGARDER     →  2. RECONNAÎTRE   →  3. COMPRENDRE         →  4. PRÉVENIR
caméra             YOLO détecte        quelle place est        message MQTT
                   les voitures        occupée ? anomalies ?   vers le serveur
```

**Privacy by design** : les images sont analysées en mémoire puis supprimées.
Aucune image, aucune vidéo, aucune plaque n'est enregistrée ni envoyée :
seuls de petits messages texte (état des places, événements) quittent le Pi.

## Avancement

| Sprint | Tâche | État |
|---|---|---|
| S1 | Structure du dossier, contrats MQTT et polygones, test YOLO pré-entraîné, photo prise par le Pi | **fait** |
| S2 | Annotation du dataset, **lot 1** (sans maquette : datasets publics et photos de test, voir [Dataset — lot 1](#dataset--lot-1)) | **fait** |
| S2 | Maquette (avec l'équipe) | **pas encore construite** |
| S3 | Annotation des lots 2 et 3 à partir des photos de la maquette (mêmes scripts) | à faire |
| S4 | Fine-tuning YOLO11n sur les 3 lots, export NCNN, optimisation sur le Pi | à faire |
| S4–S5 | Calcul d'occupation, lissage, anomalies | à faire |
| S4–S5 | Client MQTT (TLS, file locale hors ligne) | à faire |
| S6–S8 | Mesures de performance | à faire |
| S9–S10 | Intégration, démo, documentation | à faire |

## Installation (PC ou Raspberry Pi)

```bash
python -m venv venv
source venv/bin/activate        # Windows : venv\Scripts\activate
pip install -r requirements.txt
```

## Étape 0 — Tester YOLO sur des voitures miniatures

1. Mettre 10 à 20 photos de voitures jouets **vues du dessus** dans `photos_test/`.
2. Lancer :
   ```bash
   python test_yolo.py --photos photos_test/
   ```
3. Regarder les images annotées dans `resultats/<type>/` (par exemple `resultats/world/`).

## Étape 1 — Caméra du Raspberry Pi (sprint 1)

Installation du Pi et de la caméra : voir [`../docs/installation-pi.md`](../docs/installation-pi.md).

Prendre une photo (depuis la racine du dépôt) :

```bash
python iot/capture.py                                # une photo dans iot/photos_test/
python iot/capture.py --largeur 1920 --hauteur 1080  # autre résolution
python iot/capture.py --nom maquette.jpg             # nom de fichier choisi
python iot/capture.py --nombre 10 --intervalle 3     # 10 photos, une toutes les 3 s
python iot/capture.py --source opencv --camera 0     # forcer une webcam USB
```

Le script choisit tout seul le module caméra (picamera2) s'il en trouve un, sinon une webcam (OpenCV),
et affiche une **mesure de netteté** pour chaque photo (variance du laplacien : plus elle est élevée,
plus l'image est nette ; la valeur dépend de la scène, elle sert à comparer deux réglages).

Les photos vont dans `iot/photos_test/`, dossier ignoré par Git. Elles servent à régler la caméra et à
constituer le dataset de la maquette. Dans le fonctionnement normal du système, **les images ne sont
jamais enregistrées** (privacy by design).

Tests (sans caméra) :

```bash
cd iot
python -m unittest test_capture -v
```

## Contrat MQTT (à valider avec la partie serveur)

| Topic | Exemple de message | Envoyé quand |
|---|---|---|
| `parking/P1/places/<id>` | `{"place":"A3","etat":"occupee","depuis":"2026-11-20T10:02:11Z"}` | une place change d'état (retain, QoS 1) |
| `parking/P1/evenements` | `{"type":"cheval","places":["A3","A4"],"ts":"..."}` | début d'une anomalie |
| `parking/P1/evenements` | `{"type":"zone_interdite","zone":"allee1","ts":"..."}` | début d'une anomalie |
| `parking/P1/pi/statut` | `{"en_ligne":true,"fps":0.8,"temp_cpu":61}` | toutes les 60 s |

- `etat` : `libre` ou `occupee`.
- La règle « véhicule ventouse » est calculée par le serveur à partir du champ `depuis`.
- Connexion au broker Mosquitto en TLS (port 8883).

## Contrat des polygones (à valider avec la partie application)

Le fichier `places.json` décrit le contour de chaque place sur l'image de la caméra
(coordonnées en pixels). Il sera généré par l'outil de calibration de l'application ;
`places.exemple.json` sert de modèle en attendant.

## Structure prévue

```
iot/
├── README.md
├── requirements.txt
├── places.exemple.json
├── test_yolo.py          # S1 : test du modèle pré-entraîné
├── capture.py            # S1 : prise de photo (module caméra ou webcam)
├── test_capture.py       # S1 : tests de capture.py
├── .env.exemple          # S2 : modèle de iot/.env (clé API Roboflow) — iot/.env n'est jamais commité
├── annotation/           # S2 : scripts du dataset (pré-annotation, préparation, vérification, Label Studio)
├── dataset/              # S2 : lots au format YOLO — NON versionné, sauf data.yaml.exemple
├── detecteur.py          # caméra + YOLO              (à venir)
├── occupation.py         # polygones, lissage, anomalies (à venir)
├── mqtt_client.py        # envoi + file locale       (à venir)
└── main.py               # boucle principale          (à venir)
```
## Résultats du test S1 — comparaison de 3 modèles pré-entraînés

Test sur 21 images de voitures miniatures vues du dessus (seuil de confiance 0,25) :

| Modèle | Images avec détection | Véhicules détectés | Temps / image (PC) |
|---|---|---|---|
| YOLO11n (COCO) | 5 / 21 | 16 | 0,16 s |
| YOLO11n-OBB (DOTA, aérien) | 1 / 21 | 2 | 0,52 s |
| YOLOv8s-World (texte « toy car ») | 19 / 21 | 52 | 0,35 s |

**Conclusions :**
- Le modèle COCO (photos prises au sol) et le modèle OBB (images satellites, petits objets)
  ne reconnaissent pas les voitures miniatures vues du dessus.
- YOLO-World, guidé par une description en texte, détecte des voitures sur presque toutes
  les images, mais il est trop lourd pour le Raspberry Pi.
- **Plan retenu :** utiliser YOLO-World pour pré-annoter automatiquement les photos de la
  maquette, puis fine-tuner YOLO11n (léger) pour le déploiement sur le Pi (S3–S4).

  - Vérification visuelle : aucune fausse détection ; les oublis concernent les voitures
  sombres sur fond sombre, les voitures inclinées et les voitures coupées au bord de l'image.
  → La maquette utilisera un fond contrasté et un cadrage montrant toutes les places en entier.

## Dataset — lot 1

Sprint 2. Le dataset sert à **fine-tuner YOLO11n** au sprint 4 : une seule classe, `voiture` (identifiant 0), au format YOLO. Scripts et procédure détaillée, reproductible avec sa propre clé API : [`annotation/README.md`](annotation/README.md).

> **La maquette n'est pas encore construite.** Le lot 1 se compose donc de datasets publics et de nos photos de test. La chaîne de travail est celle prévue pour la maquette (pré-annotation YOLO-World, correction humaine dans Label Studio, export YOLO, vérification) : seule la source des images changera.

### Contenu (vérifié par `verifier_dataset.py` : aucune erreur)

| Source (préfixe) | Images | Voitures | Voitures / image | Taille des voitures* | Origine |
|---|---|---|---|---|---|
| `toycar_` | 73 | 76 | 1,0 | grandes | toy-car-detection (Roboflow, v4), annotations existantes vérifiées |
| `carpk_` | 80 | 5 059 | 63,2 | petites et moyennes | Échantillon de « CARPK 2 » (graine 42) : 74 CARPK + 6 PUCPR+ |
| `photos_` | 21 | 77 | 3,7 | moyennes et grandes | Nos photos de test, pré-annotées par YOLO-World puis corrigées à la main dans Label Studio |
| **Total** | **174** | **5 212** | 30,0 | | |

\* Taille de la boîte si l'image est ramenée à 640 × 640 : petite < 32 × 32 px, moyenne < 96 × 96 px, grande au-delà.

**Points de vigilance :**
- **Vues de toy-car-detection** (73 images classées une par une, détail dans `annotation/vues_toycar.csv`) : **21 vues du dessus**, **22 vues en plongée** (de biais, d'en haut) et **30 vues de côté ou de face**. Notre caméra regarde du dessus : ce dataset sert surtout à la variété (couleurs, formes de voitures miniatures).
- **Échelle** : CARPK montre beaucoup de petites voitures réelles, toy-car une grande voiture par image. Sur la maquette, les voitures seront de taille moyenne : **les photos de la maquette (lots 2 et 3) restent indispensables**.
- **Nettoyage de toy-car-detection** :
  - export augmenté × 3 : une seule variante gardée par image d'origine ;
  - polygones convertis en rectangles ;
  - classe `objects` supprimée (elle doublait une voiture déjà annotée, recouvrement de 99 %) ;
  - 5 boîtes en double fusionnées ;
  - 31 images sans voiture ignorées (pièces, écrans, visage d'une personne) ;
  - 3 images exclues (`annotation/exclusions_toycar.txt`) ;
  - 1 oubli corrigé : la camionnette rouge de `toy-car-21` (`annotation/corrections/`).
- **Photos de test** : les 4 fichiers AVIF de `photos_test/` ne sont pas lisibles par OpenCV et ont été ignorés (21 photos sur 25).

### Sources, licences et citations

| Dataset | Licence | Conditions |
|---|---|---|
| **toy-car-detection** (workspace Roboflow `parkitnowprototype`) | **CC BY 4.0** | Utilisation et modification libres, à condition de citer l'auteur |
| **CARPK** et **PUCPR+** (Hsieh et al., ICCV 2017), via le projet Roboflow « CARPK 2 » (`anis-elahi`) | Réservé à la recherche | **Usage académique uniquement**, selon les auteurs (https://lafi.github.io/LPN/). Une partie des images PUCPR+ vient de PKLot ; les droits restent à leurs propriétaires. La mention CC BY 4.0 affichée sur Roboflow vient de la personne qui a déposé le dataset, pas des auteurs. Le fork Roboflow utilisé pour le téléchargement a été supprimé. |
| **Photos de test** (`iot/photos_test/`) | Droits d'auteur (images trouvées sur Internet, dont certaines avec filigrane) | Usage personnel de test uniquement ; jamais publiées, jamais envoyées sur Roboflow |

**Images jamais versionnées** : aucune image ni aucun label de `iot/dataset/` n'est dans Git (voir `.gitignore`). Seuls sont versionnés les scripts, `data.yaml.exemple`, la liste des exclusions, le classement des vues et les corrections manuelles (texte).

Citations :

```bibtex
@misc{toy-car-detection-f5tno_dataset,
  title        = {toy-car-detection Dataset},
  type         = {Open Source Dataset},
  author       = {ParkItNowPrototype},
  howpublished = {\url{https://universe.roboflow.com/parkitnowprototype/toy-car-detection-f5tno}},
  journal      = {Roboflow Universe},
  publisher    = {Roboflow},
  year         = {2025},
  note         = {version 4, licence CC BY 4.0, consulté le 10 novembre 2026}
}

@inproceedings{Hsieh_2017_ICCV,
  author       = {Meng-Ru Hsieh and Yen-Liang Lin and Winston H. Hsu},
  booktitle    = {The IEEE International Conference on Computer Vision (ICCV)},
  title        = {Drone-based Object Counting by Spatially Regularized Regional Proposal Networks},
  year         = {2017},
  organization = {IEEE}
}
```

### Reproduire le lot 1

Détail, résultats attendus et démarche du fork CARPK : [`annotation/README.md`](annotation/README.md). En résumé (depuis la racine du dépôt, avec sa clé dans `iot/.env`, voir `iot/.env.exemple`) :

```
python iot/annotation/telecharger_roboflow.py --projet parkitnowprototype/toy-car-detection-f5tno --version 4 --sortie iot/dataset/sources/toycar_v4
python iot/annotation/telecharger_roboflow.py --projet <votre-workspace>/carpk-2-xxxxx --version 1 --sortie iot/dataset/sources/carpk_v1
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/toycar_v4 --nom toycar --classes-a-garder toycar --une-variante-par-origine --ignorer-vides --exclusions iot/annotation/exclusions_toycar.txt --corrections iot/annotation/corrections
python iot/annotation/preparer_datasets.py --source iot/dataset/sources/carpk_v1 --nom carpk --echantillon 80
python iot/annotation/pre_annoter.py --entree iot/photos_test --sortie iot/dataset/pre_annotations/photos --format label-studio
#   -> correction dans Label Studio, export JSON (annotation/README.md, section 5)
python iot/annotation/label_studio.py depuis-ls --export iot/dataset/exports/photos.label-studio.json --sortie iot/dataset/corrections/photos
python iot/annotation/preparer_datasets.py --source iot/dataset/corrections/photos --nom photos --seuil-doublons 0
python iot/annotation/verifier_dataset.py --apercu 12
```

Emplacement, **non versionné** :

```
iot/dataset/
├── data.yaml.exemple        # seul fichier versionné : modèle de configuration YOLO (copié en data.yaml au sprint 4)
├── sources/                 # téléchargements Roboflow (toycar_v4/, carpk_v1/)
├── pre_annotations/photos/  # sortie de pre_annoter.py + fichier d'import Label Studio
├── exports/                 # exports JSON de Label Studio
├── corrections/photos/      # photos corrigées, au format YOLO
├── lot1/                    # LE LOT 1 : images/ + labels/ (toycar_*, carpk_*, photos_*)
└── apercu/                  # images de contrôle avec les boîtes dessinées
```

### Quand la maquette sera prête (lots 2 et 3)

1. **Photos** : sur le Pi, avec le cadrage définitif de la caméra fixe (contrat §4.4), prendre des séries variées. Faire varier l'éclairage, le nombre de voitures, les places vides et pleines, les voitures à cheval, les voitures en zone interdite et les couleurs (pas de voiture noire) :
   ```
   python iot/capture.py --sortie iot/dataset/maquette/brut --largeur 640 --hauteur 480 --nombre 30 --intervalle 5
   ```
   Déplacer les voitures entre deux photos. Viser environ 150 photos pour les lots 2 et 3.
2. **Pré-annoter** (même script, autre dossier d'entrée) :
   ```
   python iot/annotation/pre_annoter.py --entree iot/dataset/maquette/brut --sortie iot/dataset/pre_annotations/maquette --format label-studio
   ```
3. **Corriger** dans Label Studio : nouveau projet, stockage *Local files* sur `…\iot\dataset\pre_annotations\maquette\images`, import du JSON, puis export JSON (`annotation/README.md`, section 5).
4. **Ajouter** au lot 2 :
   ```
   python iot/annotation/label_studio.py depuis-ls --export iot/dataset/exports/maquette.label-studio.json --sortie iot/dataset/corrections/maquette
   python iot/annotation/preparer_datasets.py --source iot/dataset/corrections/maquette --nom maquette --sortie iot/dataset/lot2 --seuil-doublons 0
   python iot/annotation/verifier_dataset.py --dossier iot/dataset/lot2 --apercu 12
   ```
5. Au sprint 4 : répartir les lots en `entrainement/` et `validation/`, puis copier `dataset/data.yaml.exemple` en `dataset/data.yaml`.

Les photos de la maquette nous appartiennent : elles pourront être partagées dans l'équipe, mais **elles ne vont pas non plus dans Git** (taille du dépôt).
