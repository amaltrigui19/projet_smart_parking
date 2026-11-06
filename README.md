# Smart Parking

Parking intelligent par vision par ordinateur — Module **Cloud of Things**, SUP'COM (École Supérieure des Communications de Tunis).

**Auteurs :** Eya Kammoun, Amal Trigui, Maissa Ben Mrad
**Encadrant :** Dr. Ing. Mohamed-Bécha Kaâniche

---

## Présentation

Les solutions IoT classiques de parking reposent sur un capteur par place : câblage, coût et maintenance augmentent avec la taille du parking. Ce projet remplace ces capteurs par **une caméra** associée à un modèle de vision par ordinateur : une seule caméra couvre plusieurs places.

Le système ne se limite pas à détecter si une place est libre. Il :

- mesure l'occupation de chaque place en temps réel ;
- guide le conducteur vers la place libre la plus proche (plus court chemin Dijkstra/A\* sur un graphe du parking) ;
- détecte les stationnements gênants (voiture à cheval sur deux places, véhicule ventouse, place spéciale occupée sans autorisation, véhicule en zone interdite) ;
- permet la réservation et le paiement (simulé) à distance ;
- alerte l'exploitant par notification web push ;
- respecte la vie privée dès la conception (*privacy by design*) : traitement local des images, aucune plaque ni visage, aucun enregistrement vidéo.

## Architecture

```
Caméra ──images──▶ Raspberry Pi 4 ──MQTT (TLS)──▶ Broker Mosquitto ──événements──▶ Serveur Jakarta EE (WildFly) ◀──HTTPS/JWT, Web Push──▶ PWA
                   (YOLO + OpenCV)                                                          │
                                                                                             ▼
                                                                                          MongoDB
```

Le Raspberry Pi analyse les images localement et ne publie sur MQTT que l'état des places et les événements détectés. Le serveur applique les règles métier, calcule les trajets, enregistre les données dans MongoDB et expose une API REST sécurisée par OAuth2/JWT.

### Déploiement

| Nœud | Contenu |
|---|---|
| Raspberry Pi 4 (sur site) | Détecteur YOLO (Python), client MQTT, caméra |
| Serveur cloud IaaS (VM) | Broker Mosquitto (TLS, port 8883), WildFly (API REST `.war`, HTTPS 443), MongoDB (27017) |
| Navigateur / mobile | PWA unique (interface conducteur et interface exploitant) |

## Choix technologiques

| Couche | Technologie | Rôle |
|---|---|---|
| Vision par ordinateur | Python, OpenCV, YOLO léger (YOLO11n ; NCNN ou TFLite sur le Pi) | Détection des véhicules, occupation, anomalies |
| IoT | MQTT, Mosquitto | Transport des états et événements Pi → serveur |
| Middleware | Jakarta EE 11, JAX-RS, WildFly | Règles métier, calcul des trajets, API REST, OAuth2/JWT |
| Base de données | MongoDB | Places, plan (graphe), réservations, utilisateurs, événements |
| Front-end | PWA (HTML, CSS, JavaScript) | Interfaces conducteur et exploitant, installable |
| Notifications | Web Push (service worker, clés VAPID) | Alertes conducteur et exploitant |
| Déploiement | Cloud IaaS, GitHub | Hébergement, code source |

## Fonctionnalités

### Caméra et IoT
- **Occupation** : chaque place est un polygone sur l'image, occupée si la boîte d'un véhicule la recouvre suffisamment.
- **Voiture à cheval** sur deux places.
- **Véhicule ventouse** : chronomètre par place, événement si la durée autorisée est dépassée.
- **Place spéciale** (PMR, recharge électrique, réservée) occupée sans autorisation enregistrée.
- **Zone interdite** (allée, sortie) : véhicule détecté → événement.

### Guidage
Parking modélisé en graphe (entrée, intersections, places). À chaque changement d'occupation, le plus court chemin vers la place libre la plus proche est recalculé. La place proposée est verrouillée 2 à 3 minutes pour éviter d'envoyer deux conducteurs vers la même place.

### Application PWA (deux interfaces, selon le rôle JWT)
- **Conducteur** : inscription/connexion, plan en temps réel, guidage, réservation, paiement simulé, historique, notifications.
- **Exploitant** : tableau de bord temps réel, alertes et événements, configuration du parking (plan, zones, type de place, durée maximale), statistiques (taux d'occupation, heures de pointe, durée moyenne, infractions), gestion des utilisateurs, réservations et autorisations.

Le serveur vérifie le rôle à chaque appel d'API : un conducteur n'accède jamais aux données d'exploitation.

## Structure du dépôt

```
.
├── iot/      # Raspberry Pi : détection YOLO, occupation, client MQTT
├── api/      # Serveur Jakarta EE : API REST, JWT, guidage, MongoDB
├── pwa/      # Application web progressive (conducteur + exploitant)
├── infra/    # docker-compose : Nginx (HTTPS), Mosquitto (TLS), MongoDB, WildFly
├── docs/     # User stories, contrats (MQTT, API, push, plan), installation, UML, manuel
└── README.md
```

Documents de référence : [user stories](docs/user-stories.md), [contrats d'interface](docs/contrats.md), [installation du serveur](docs/installation-serveur.md).

## Installation

### Partie IoT (`iot/`)

Prérequis : Python 3, Raspberry Pi 4 (4 Go min.) ou ordinateur de repli, caméra.

```bash
python -m venv .venv            # à la racine du dépôt
# Windows : .venv\Scripts\activate    |    Linux/macOS : source .venv/bin/activate
pip install -r iot/requirements.txt
```

Le format du fichier des places (polygones) est illustré par `iot/places.exemple.json`. Un test rapide du modèle est disponible dans `iot/test_yolo.py`.

### Serveur et PWA

Tout le serveur (Nginx en HTTPS, Mosquitto en TLS, MongoDB, WildFly) se lance avec Docker, en local comme sur la VM cloud :

```bash
cp infra/.env.example infra/.env          # puis remplir les mots de passe
./infra/scripts/generer-certificats.sh
cd infra && docker compose up -d --build
cd .. && ./infra/scripts/verifier.sh      # → "Tout fonctionne."
```

Procédure complète (voie locale et VM cloud avec Let's Encrypt) : [`docs/installation-serveur.md`](docs/installation-serveur.md).

## Planning (10 sprints)

| Sprint | Dates | Livrable principal |
|---|---|---|
| 1 | 2–6 nov. | User stories, contrats, VM en HTTPS, PWA de base, photo prise par le Pi |
| 2 | 9–13 nov. | Maquette, MQTT sécurisé, UML, outil de dessin des polygones |
| 3 | 16–20 nov. | Dataset annoté, connexion JWT, polygones enregistrés |
| 4 | 23–27 nov. | Modèle YOLO, plan du parking en temps réel (API + WebSocket) |
| 5 | 30 nov.–4 déc. | Occupation calculée, serveur recevant MQTT, tableau de bord exploitant |
| 6 | 7–11 déc. | Première chaîne complète caméra → écran, réservation |
| 7 | 14–18 déc. | Modèle sur le Pi, paiement simulé, plus court chemin |
| 8 | 21–25 déc. | Trajet affiché, verrouillage, anomalies, règles d'alerte |
| 9 | 28 déc.–1er janv. | Notifications push, statistiques, évaluation du modèle |
| 10 | 4–8 janv. | Déploiement final, documentation, manuel, vidéo, démonstration |

## Méthodologie

Extreme Programming (XP) : user stories, itérations courtes, programmation en binôme sur les parties difficiles, intégration continue sur GitHub, tests unitaires puis tests d'acceptation sur la maquette. Chaque membre commite à chaque créneau de travail.

## Limites

- La précision dépend de la lumière et de l'angle de la caméra.
- Le Raspberry Pi limite la fréquence d'analyse (environ 1 image toutes les 1 à 2 secondes).
- Les zones des places doivent être configurées pour chaque parking.
- Une connexion Internet est nécessaire (file locale des événements sur le Pi en cas de coupure, renvoi à la reconnexion).
- La caméra ne vérifie pas les badges : les alertes de place spéciale reposent sur les autorisations enregistrées.

**Hors périmètre :** reconnaissance de plaques ou de personnes, verbalisation automatique, guidage routier en ville, transactions bancaires réelles (paiement simulé).

## Livrables

Cahier des charges, code source (API, PWA, passerelle IoT), documentation technique, manuel utilisateur, API testable avec Postman, supports marketing, vidéo de démonstration (5 minutes max).
