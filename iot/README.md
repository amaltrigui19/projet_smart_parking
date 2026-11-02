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

| Semaine | Tâche | État |
|---|---|---|
| S1 | Structure du dossier, contrats MQTT et polygones, test YOLO pré-entraîné | en cours |
| S2 | Installation du Raspberry Pi + caméra | à faire |
| S2–S3 | Maquette (avec l'équipe) | à faire |
| S3–S4 | Dataset + fine-tuning YOLO (si nécessaire) | à faire |
| S4 | Export NCNN, optimisation sur le Pi | à faire |
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
3. Regarder les images annotées dans `resultats/test_pretrained/`.

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